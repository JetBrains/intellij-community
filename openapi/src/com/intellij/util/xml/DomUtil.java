/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomUtil");

  public static Class getGenericValueType(Type type) {
    return getClassFromGenericType(GenericValue.class.getTypeParameters()[0], type);
  }

  public static Class extractParameterClassFromGenericType(Type type) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      final Type rawType = parameterizedType.getRawType();

      if (isGenericValue(rawType)) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length == 1 && arguments[0] instanceof Class) {
          return (Class)arguments[0];
        }
      } else {
        for (final Type t : ((Class)rawType).getGenericInterfaces()) {
          final Class aClass = extractParameterClassFromGenericType(t);
          if (aClass != null) {
            return aClass;
          }
        }
      }
    } else if (type instanceof Class) {
      for (final Type t : ((Class)type).getGenericInterfaces()) {
        final Class aClass = extractParameterClassFromGenericType(t);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return null;
  }

  private static boolean isGenericValue(final Type rawType) {
    return rawType == GenericDomValue.class || rawType == GenericAttributeValue.class;
  }

  public static boolean isGenericValueType(Type type) {
    return getGenericValueType(type) != null;
  }

  private static Type resolveVariable(TypeVariable variable, final Class classType) {
    final Class aClass = DomUtil.getRawType(classType);
    int index = ContainerUtil.findByEquals(aClass.getTypeParameters(), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = aClass.getInterfaces();
    final Type[] genericInterfaces = aClass.getGenericInterfaces();
    for (int i = 0; i < classes.length; i++) {
      Class anInterface = classes[i];
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable typeVariable = (TypeVariable)resolved;
        index = ContainerUtil.findByEquals(anInterface.getTypeParameters(), typeVariable);
        assert index >= 0 : typeVariable + " " + Arrays.asList(anInterface.getTypeParameters());
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

  public static Class<?> getClassFromGenericType(final Type genericType, final Type classType) {
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
        final int index = ContainerUtil.findByEquals(aClass.getTypeParameters(), type);
        if (index >= 0) {
          return getRawType(((ParameterizedType)classType).getActualTypeArguments()[index]);
        }
      }
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
              if (getGenericValueType(argument) != null) {
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

  public static boolean isDomElement(final Type type) {
    return type != null && DomElement.class.isAssignableFrom(getRawType(type));
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

  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType) {
    final NameStrategy annotation = findAnnotationDFS(rawType, NameStrategy.class);
    if (annotation != null) {
      final Class aClass = annotation.value();
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  public static boolean isTagValueGetter(final Method method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      return true;
    }
    return false;
  }

  private static boolean hasTagValueAnnotation(final Method method) {
    return findAnnotationDFS(method, TagValue.class) != null;
  }

  public static <T extends Annotation> T findAnnotationDFS(final Method method, final Class<T> annotationClass) {
    return JavaMethodSignature.getSignature(method).findAnnotation(annotationClass, method.getDeclaringClass());
  }

  public static boolean isGetter(final Method method) {
    final String name = method.getName();
    if (method.getParameterTypes().length != 0) {
      return false;
    }
    final Class<?> returnType = method.getReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    if (name.startsWith("is")) {
      return canHaveIsPropertyGetterPrefix(method.getGenericReturnType());
    }
    return false;
  }

  public static boolean canHaveIsPropertyGetterPrefix(final Type type) {
    return boolean.class.equals(type) || Boolean.class.equals(type)
           || Boolean.class.equals(getGenericValueType(type));
  }

  public final static void tryAccept(final DomElementVisitor visitor, final Class aClass, DomElement proxy) {
    try {
      tryInvoke(visitor, "visit" + aClass.getSimpleName(), aClass, proxy);
    }
    catch (NoSuchMethodException e) {
      try {
        tryInvoke(visitor, "visit", aClass, proxy);
      }
      catch (NoSuchMethodException e1) {
        for (Class aClass1 : aClass.getInterfaces()) {
          tryAccept(visitor, aClass1, proxy);
        }
      }
    }
  }

  private static void tryInvoke(final DomElementVisitor visitor, final String name, final Class aClass, DomElement proxy) throws NoSuchMethodException {
    try {
      final Method method = visitor.getClass().getMethod(name, aClass);
      method.setAccessible(true);
      method.invoke(visitor, proxy);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
  }

  public static boolean isTagValueSetter(final Method method) {
    boolean setter = method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }
}
