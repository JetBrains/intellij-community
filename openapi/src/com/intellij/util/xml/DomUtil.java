/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomUtil {

  private DomUtil() {
  }

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
  public static <T extends DomElement> T findByName(@NotNull Collection<T> list, @NotNull String name) {
    for (T element: list) {
      String elementName = element.getGenericInfo().getElementName(element);
      if (elementName != null && elementName.equals(name)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  public static String[] getElementNames(@NotNull Collection<? extends DomElement> list) {
    ArrayList<String> result = new ArrayList<String>(list.size());
    if (list.size() > 0) {
      for (DomElement element: list) {
        String name = element.getGenericInfo().getElementName(element);
        if (name != null) {
          result.add(name);
        }
      }
    }
    return result.toArray(new String[result.size()]);
  }

  @NotNull
  public static List<XmlTag> getElementTags(@NotNull Collection<? extends DomElement> list) {
    ArrayList<XmlTag> result = new ArrayList<XmlTag>(list.size());
    if (list.size() > 0) {
      for (DomElement element: list) {
        XmlTag tag = element.getXmlTag();
        if (tag != null) {
          result.add(tag);
        }
      }
    }
    return result;
  }
}
