/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.util.ReflectionCache;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {
  private static final TypeVariable GENERIC_VALUE_TYPE_VARIABLE = ReflectionCache.getTypeParameters(GenericValue.class)[0];

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

  @NotNull
  public static TextRange getValueRange(GenericDomValue domValue) {
    if (domValue instanceof GenericAttributeValue) {
      return new TextRange(1, ((GenericAttributeValue)domValue).getXmlAttributeValue().getTextLength() - 1);
    } else {
      final XmlTag tag = domValue.getXmlTag();
      assert tag != null;
      XmlTagValue tagValue = tag.getValue();
      final TextRange valueRange = tagValue.getTextRange();
      final int tagOffset = tag.getTextOffset();
      return new TextRange(valueRange.getStartOffset() - tagOffset, valueRange.getEndOffset() - tagOffset);
    }
  }

}
