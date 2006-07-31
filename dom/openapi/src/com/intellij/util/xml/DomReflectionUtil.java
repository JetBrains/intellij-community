/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomReflectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomReflectionUtil");
  private DomReflectionUtil() {
  }

  public static Object invokeMethod(final JavaMethodSignature method, final Object object, final Object... args) {
    return invokeMethod(method.findMethod(object.getClass()), object, args);
  }

  public static Object invokeMethod(final Method method, final Object object, final Object... args) {
    try {
      return method.invoke(object, args);
    }
    catch (IllegalArgumentException e) {
      throw new RuntimeException("Calling method " + method + " on object " + object + " with arguments " + Arrays.asList(args), e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      else if (cause instanceof Error) {
        throw (Error)cause;
      }
      else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new RuntimeException(e);
    }
  }

  public static Type resolveVariable(TypeVariable variable, final Class classType) {
    final Class aClass = getRawType(classType);
    int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = ReflectionCache.getInterfaces(aClass);
    final Type[] genericInterfaces = ReflectionCache.getGenericInterfaces(aClass);
    for (int i = 0; i < classes.length; i++) {
      Class anInterface = classes[i];
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable typeVariable = (TypeVariable)resolved;
        index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(anInterface), typeVariable);
        LOG.assertTrue(index >= 0, "Cannot resolve type variable:\n" +
                            "typeVariable = " + typeVariable + "\n" +
                            "genericDeclaration = " + declarationToString(typeVariable.getGenericDeclaration()) + "\n" +
                            "searching in " + declarationToString(anInterface));
        final Type type = genericInterfaces[i];
        if (type instanceof Class) {
          return Object.class;
        }
        if (type instanceof ParameterizedType) {
          return ((ParameterizedType)type).getActualTypeArguments()[index];
        }
        throw new AssertionError("Invalid type: " + type);
      }
    }
    return null;
  }

  private static String declarationToString(final GenericDeclaration anInterface) {
    return anInterface.toString() + Arrays.asList(anInterface.getTypeParameters()) + " loaded by " + ((Class)anInterface).getClassLoader();
  }

  public static Class<?> substituteGenericType(final Type genericType, final Type classType) {
    if (genericType instanceof TypeVariable) {
      final Class<?> aClass = getRawType(classType);
      final Type type = resolveVariable((TypeVariable)genericType, aClass);
      if (type instanceof Class) {
        return (Class)type;
      }
      if (type instanceof ParameterizedType) {
        return (Class<?>)((ParameterizedType)type).getRawType();
      }
      if (type instanceof TypeVariable && classType instanceof ParameterizedType) {
        final int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), type);
        if (index >= 0) {
          return getRawType(((ParameterizedType)classType).getActualTypeArguments()[index]);
        }
      }
    } else {
      return getRawType(genericType);
    }
    return null;
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    assert false : type;
    return null;
  }

  public static <T extends Annotation> T findAnnotationDFS(final Class<?> rawType, final Class<T> annotationType) {
    T annotation = rawType.getAnnotation(annotationType);
    if (annotation != null) return annotation;

    for (Class aClass : rawType.getInterfaces()) {
      annotation = findAnnotationDFS(aClass, annotationType);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  public static <T extends Annotation> T findAnnotationDFS(final Method method, final Class<T> annotationClass) {
    return JavaMethodSignature.getSignature(method).findAnnotation(annotationClass, method.getDeclaringClass());
  }

  @Nullable
  public static Type extractCollectionElementType(Type returnType) {
    if (returnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)returnType;
      final Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class) {
        final Class<?> rawClass = (Class<?>)rawType;
        if (List.class.equals(rawClass) || Collection.class.equals(rawClass)) {
          final Type[] arguments = parameterizedType.getActualTypeArguments();
          if (arguments.length == 1) {
            final Type argument = arguments[0];
            if (argument instanceof WildcardType) {
              final Type[] upperBounds = ((WildcardType)argument).getUpperBounds();
              if (upperBounds.length == 1) {
                return upperBounds[0];
              }
            }
            else if (argument instanceof ParameterizedType) {
              if (DomUtil.getGenericValueParameter(argument) != null) {
                return argument;
              }
            }
            else if (argument instanceof Class) {
              return argument;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean canHaveIsPropertyGetterPrefix(final Type type) {
    return boolean.class.equals(type) || Boolean.class.equals(type)
           || Boolean.class.equals(DomUtil.getGenericValueParameter(type));
  }

  public static Method[] getGetterMethods(final String[] path, final Class<? extends DomElement> startClass) {
    final Method[] methods = new Method[path.length];
    Class aClass = startClass;
    for (int i = 0; i < path.length; i++) {
      final Method getter = findGetter(aClass, path[i]);
      assert getter != null : "Couldn't find getter for property " + path[i] + " in class " + aClass;
      methods[i] = getter;
      aClass = getter.getReturnType();
      if (List.class.isAssignableFrom(aClass)) {
        aClass = getRawType(extractCollectionElementType(getter.getGenericReturnType()));
      }
    }
    return methods;
  }

  @Nullable
  public static Method findGetter(Class aClass, String propertyName) {
    final String capitalized = StringUtil.capitalize(propertyName);
    try {
      return aClass.getMethod("get" + capitalized);
    }
    catch (NoSuchMethodException e) {
      final Method method;
      try {
        method = aClass.getMethod("is" + capitalized);
        return canHaveIsPropertyGetterPrefix(method.getGenericReturnType()) ? method : null;
      }
      catch (NoSuchMethodException e1) {
        return null;
      }
    }
  }
}
