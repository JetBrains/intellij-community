/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {
  public static final TypeVariable<Class<GenericValue>> GENERIC_VALUE_TYPE_VARIABLE = ReflectionCache.getTypeParameters(GenericValue.class)[0];

  private DomUtil() {
  }

  public static Class extractParameterClassFromGenericType(Type type) {
    return getGenericValueParameter(type);
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
  public static List<JavaMethod> getFixedPath(DomElement element) {
    assert element.isValid();
    final LinkedList<JavaMethod> methods = new LinkedList<JavaMethod>();
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
    return DomReflectionUtil.substituteGenericType(GENERIC_VALUE_TYPE_VARIABLE, type);
  }

  @Nullable
  public static XmlElement getValueElement(GenericDomValue domValue) {
    if (domValue instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)domValue).getXmlAttributeValue();
    } else {
      return domValue.getXmlTag();
    }
  }

  public static List<? extends DomElement> getIdentitySiblings(DomElement element) {
    final Method nameValueMethod = ElementPresentationManager.findNameValueMethod(element.getClass());
    if (nameValueMethod != null) {
      final NameValue nameValue = DomReflectionUtil.findAnnotationDFS(nameValueMethod, NameValue.class);
      if (nameValue == null || nameValue.unique()) {
        final Object o = DomReflectionUtil.invokeMethod(nameValueMethod, element);
        if (o instanceof GenericDomValue) {
          final GenericDomValue genericDomValue = (GenericDomValue)o;
          final String stringValue = genericDomValue.getStringValue();
          if (stringValue != null) {
            final DomElement parent = element.getManager().getIdentityScope(element);
            final DomGenericInfo domGenericInfo = parent.getGenericInfo();
            final String tagName = element.getXmlElementName();
            final DomCollectionChildDescription childDescription = domGenericInfo.getCollectionChildDescription(tagName);
            if (childDescription != null) {
              final ArrayList<DomElement> list = new ArrayList<DomElement>(childDescription.getValues(parent));
              list.remove(element);
              return list;
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static DomElement findDuplicateNamedValue(DomElement element, String newName) {
    return ElementPresentationManager.findByName(getIdentitySiblings(element), newName);
  }

}
