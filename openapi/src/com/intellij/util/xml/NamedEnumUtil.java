/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;

/**
 * @author peter
 */
public class NamedEnumUtil {

  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value) {
    if (NamedEnum.class.isAssignableFrom(enumClass)) {
      for (final T t : enumClass.getEnumConstants()) {
        if (Comparing.equal(((NamedEnum)t).getValue(), value)) {
          return t;
        }
      }
    } else {
      return (T) Enum.valueOf(enumClass, value);
    }
    return null;
  }

  public static <T extends Enum> String getEnumValueByElement(final T element) {
    if (element == null) return null;
    if (NamedEnum.class.isAssignableFrom(element.getClass())) {
      return ((NamedEnum) element).getValue();
    } else {
      return element.name();
    }
  }

}
