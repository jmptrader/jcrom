/**
 * This file is part of the JCROM project.
 * Copyright (C) 2008-2015 - All rights reserved.
 * Authors: Olafur Gauti Gudmundsson, Nicolas Dos Santos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jcrom.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.jcrom.annotations.JcrNode;
import org.jcrom.converter.Converter;
import org.jcrom.type.TypeHandler;

/**
 * Various reflection utility methods, used mainly in the Mapper.
 *
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public final class ReflectionUtils {

    private ReflectionUtils() {
    }

    /**
     * Get an array of all fields declared in the supplied class,
     * and all its superclasses (except java.lang.Object).
     *
     * @param type the class for which we want to retrieve the Fields
     * @param returnFinalFields specifies whether to return final fields
     * @return an array of all declared and inherited fields
     */
    public static Field[] getDeclaredAndInheritedFields(Class<?> type, boolean returnFinalFields) {
        List<Field> allFields = new ArrayList<Field>();
        allFields.addAll(getValidFields(type.getDeclaredFields(), returnFinalFields));
        Class<?> parent = type.getSuperclass();
        while (parent != null && parent != Object.class) {
            allFields.addAll(getValidFields(parent.getDeclaredFields(), returnFinalFields));
            parent = parent.getSuperclass();
        }
        return allFields.toArray(new Field[allFields.size()]);
    }

    public static List<Field> getValidFields(Field[] fields, boolean returnFinalFields) {
        List<Field> validFields = new ArrayList<Field>();
        // we ignore static and final fields
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) && (returnFinalFields || !Modifier.isFinal(field.getModifiers()))) {
                validFields.add(field);
            }
        }
        return validFields;
    }

    /**
     * Retrieve a method from a given class or one of its superclass.
     *
     * @param type the class for which we want to retrieve the Method
     * @param name the name of the method
     * @param parametersType the parameters type of the method
     * @return {@link Method}
     */
    public static Method getMethod(Class<?> type, String name, Class<?>... parametersType) throws NoSuchMethodException {
        try {
            return type.getMethod(name, parametersType);
        } catch (NoSuchMethodException e) {
            if (!Object.class.equals(type.getSuperclass())) {
                return getMethod(type.getSuperclass(), name, parametersType);
            }
            throw new NoSuchMethodException();
        }
    }

    /**
     * Get the (first) class that parameterizes the Type supplied.
     * 
     * @param type the Type
     * @return the class that parameterizes the field, or null if field is not parameterized
     */
    public static Class<?> getParameterizedClass(Type type) {
        return getParameterizedClass(type, 0);
    }

    /**
     * Get the class that parameterizes the Type supplied, at the index supplied (type can be parameterized with multiple param classes).
     * 
     * @param type the Type
     * @param index the index of the parameterizing class
     * @return the class that parameterizes the field, or null if field is not parameterized
     */
    public static Class<?> getParameterizedClass(Type type, int index) {
        if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            Type paramType = ptype.getActualTypeArguments()[index];
            if (paramType instanceof GenericArrayType) {
                Class<?> arrayType = (Class<?>) ((GenericArrayType) paramType).getGenericComponentType();
                return Array.newInstance(arrayType, 0).getClass();
            } else {
                if (paramType instanceof ParameterizedType) {
                    ParameterizedType paramPType = (ParameterizedType) paramType;
                    return (Class<?>) paramPType.getRawType();
                } else {
                    return (Class<?>) paramType;
                }
            }
        }
        return null;
    }

    public static Class<?> getTypeArgumentOfParameterizedClass(Type type, int index, int typeIndex) {
        if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            Type paramType = ptype.getActualTypeArguments()[index];
            if (!(paramType instanceof GenericArrayType)) {
                if (paramType instanceof ParameterizedType) {
                    ParameterizedType paramPType = (ParameterizedType) paramType;
                    Type paramParamType = paramPType.getActualTypeArguments()[typeIndex];
                    if (!(paramParamType instanceof ParameterizedType)) {
                        return (Class<?>) paramParamType;
                    }
                }
            }
        }
        return null;
    }

    public static Type getConverterGenericType(Class<? extends Converter<?, ?>> converterClass, int index) {
        Type converterType = converterClass.getGenericInterfaces()[0];
        ParameterizedType ptype = (ParameterizedType) converterType;
        Type paramType = ptype.getActualTypeArguments()[index];
        if (paramType instanceof ParameterizedType) {
            ParameterizedType paramPType = (ParameterizedType) paramType;
            return paramPType;
        }
        return null;
    }

    /**
     * Try to retrieve the generic parameter of an ObjectProperty at runtime.
     *  
     * @param source source Object
     * @param type type
     * @param genericType generic type
     * @return Class representing the generic parameter
     */
    public static Class<?> getObjectPropertyGeneric(Object source, Class<?> type, Type genericType) {
        if (genericType instanceof ParameterizedType) {
            return getGenericClass(source, (ParameterizedType) genericType);
        } else {
            return type;
        }
    }

    /**
     * Try to extract the generic type of the given ParameterizedType used in the given source object.
     *
     * @param source source Object
     * @param type ParameterizedType
     * @return Class representing the generic type
     */
    private static Class getGenericClass(Object source, ParameterizedType type) {
        Type type1 = type.getActualTypeArguments()[0];
        if (type1 instanceof ParameterizedType) {
            return (Class) ((ParameterizedType) type1).getRawType();
        } else if (type1 instanceof TypeVariable) {
            // Type is generic, try to get its actual type from the super class
            // e.g.: ObjectProperty<T> where T extends U
            if (source != null && source.getClass().getGenericSuperclass() instanceof ParameterizedType) {
                Type parameterizedType = ((ParameterizedType) source.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
                if (parameterizedType instanceof ParameterizedType) { // it means that the parent class is also generic
                    return (Class) ((ParameterizedType) parameterizedType).getRawType();
                } else {
                    return (Class) parameterizedType;
                }
            } else {
                // The actual type is not declared, use the upper bound of the type e.g. U
                return (Class) ((TypeVariable) type1).getBounds()[0];
            }
        } else {
            return (Class) type1;
        }
    }

    /**
     * Check if a field is parameterized with a specific class.
     *
     * @param field the field
     * @param c the class to check against
     * @return true if the field is parameterized and c is the class that
     * parameterizes the field, or is an interface that the parameterized class
     * implements, else false
     */
    public static boolean isFieldParameterizedWithClass(Field field, Class<?> c) {
        if (field.getGenericType() instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) field.getGenericType();
            for (Type type : ptype.getActualTypeArguments()) {
                if (type == c) {
                    return true;
                }
                if (c.isInterface() && c.isAssignableFrom((Class<?>) type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the type supplied is parameterized with a valid JCR property type.
     *
     * @param type the Type
     * @param typeHandler {@link TypeHandler}
     * @return true if the type is parameterized with a valid JCR property type, else false
     */
    public static boolean isTypeParameterizedWithPropertyType(Type type, TypeHandler typeHandler) {
        if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            for (Type t : ptype.getActualTypeArguments()) {
                if (typeHandler.isPropertyType((Class<?>) t)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static JcrNode getJcrNodeAnnotation(Class<?> c) {

        if (c.isAnnotationPresent(JcrNode.class)) {
            return c.getAnnotation(JcrNode.class);
        } else {
            // need to check all superclasses
            Class<?> parent = c.getSuperclass();
            while (parent != null && parent != Object.class) {
                if (parent.isAnnotationPresent(JcrNode.class)) {
                    return parent.getAnnotation(JcrNode.class);
                }

                // ...and interfaces that the superclass implements
                for (Class<?> interfaceClass : parent.getInterfaces()) {
                    if (interfaceClass.isAnnotationPresent(JcrNode.class)) {
                        return interfaceClass.getAnnotation(JcrNode.class);
                    }
                }

                parent = parent.getSuperclass();
            }

            // ...and all implemented interfaces
            for (Class<?> interfaceClass : c.getInterfaces()) {
                if (interfaceClass.isAnnotationPresent(JcrNode.class)) {
                    return interfaceClass.getAnnotation(JcrNode.class);
                }
            }
        }
        // no annotation found, use the defaults
        return null;
    }

    private static String stripFilenameExtension(String filename) {
        if (filename.indexOf('.') != -1) {
            return filename.substring(0, filename.lastIndexOf('.'));
        } else {
            return filename;
        }
    }

    public static Set<Class<?>> getFromDirectory(File directory, String packageName) throws ClassNotFoundException {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        if (directory.exists()) {
            for (String file : directory.list()) {
                if (file.endsWith(".class")) {
                    String name = packageName + '.' + stripFilenameExtension(file);
                    Class<?> clazz = Class.forName(name);
                    classes.add(clazz);
                }
            }
        }
        return classes;
    }

    public static Set<Class<?>> getFromJARFile(String jar, String packageName) throws IOException, FileNotFoundException, ClassNotFoundException {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        JarInputStream jarFile = new JarInputStream(new FileInputStream(jar));
        JarEntry jarEntry;
        do {
            jarEntry = jarFile.getNextJarEntry();
            if (jarEntry != null) {
                String className = jarEntry.getName();
                if (className.endsWith(".class")) {
                    className = stripFilenameExtension(className);
                    if (className.startsWith(packageName)) {
                        classes.add(Class.forName(className.replace('/', '.')));
                    }
                }
            }
        } while (jarEntry != null);
        return classes;
    }

    public static Set<Class<?>> getClasses(String packageName) throws IOException, ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return getClasses(loader, packageName);
    }

    public static Set<Class<?>> getClasses(ClassLoader loader, String packageName) throws IOException, ClassNotFoundException {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = loader.getResources(path);
        if (resources != null) {
            while (resources.hasMoreElements()) {
                String filePath = resources.nextElement().getFile();
                // WINDOWS HACK
                if (filePath.indexOf("%20") > 0) {
                    filePath = filePath.replaceAll("%20", " ");
                }
                if (filePath != null) {
                    if ((filePath.indexOf("!") > 0) & (filePath.indexOf(".jar") > 0)) {
                        String jarPath = filePath.substring(0, filePath.indexOf("!")).substring(filePath.indexOf(":") + 1);
                        // WINDOWS HACK
                        if (jarPath.indexOf(":") >= 0) {
                            jarPath = jarPath.substring(1);
                        }
                        classes.addAll(getFromJARFile(jarPath, path));
                    } else {
                        classes.addAll(getFromDirectory(new File(filePath), packageName));
                    }
                }
            }
        }
        return classes;
    }
}
