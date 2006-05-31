/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {

  private DomUtil() {
  }

  public static Class extractParameterClassFromGenericType(Type type) {
    return getGenericValueParameter(type);

    /*if (type instanceof ParameterizedType) {
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
    return null;*/
  }

  private static boolean isGenericValue(final Type rawType) {
    return rawType == GenericDomValue.class || rawType == GenericAttributeValue.class;
  }

  public static boolean isGenericValueType(Type type) {
    return getGenericValueParameter(type) != null;
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
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result.add(tag);
      }
    }
    return result;
  }

  @NotNull
  public static XmlTag[] getElementTags(@NotNull DomElement[] list) {
    XmlTag[] result = new XmlTag[list.length];
    int i = 0;
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result[i++] = tag;
      }
    }
    return result;
  }

  @Nullable
  public static List<Method> getFixedPath(DomElement element) {
    assert element.isValid();
    final LinkedList<Method> methods = new LinkedList<Method>();
    while (true) {
      final DomElement parent = element.getParent();
      if (parent instanceof DomFileElement) {
        break;
      }
      final String xmlElementName = element.getXmlElementName();
      final DomGenericInfo genericInfo = parent.getGenericInfo();

      final DomFixedChildDescription description = genericInfo.getFixedChildDescription(xmlElementName);
      if (description == null) {
        return null;
      }

      methods.addFirst(description.getGetterMethod(description.getValues(parent).indexOf(element)));
      element = element.getParent();
    }
    return methods;
  }

  public static Class getGenericValueParameter(Type type) {
    return DomReflectionUtil.substituteGenericType(GenericValue.class.getTypeParameters()[0], type);
  }
}
