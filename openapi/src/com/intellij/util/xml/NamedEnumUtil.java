/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.Function;

/**
 * @author peter
 */
public class NamedEnumUtil {
  private static final Function<Enum, String> NAMED_SHOW = new Function<Enum, String>() {
    public String fun(final Enum s) {
      return ((NamedEnum) s).getValue();
    }
  };
  private static final Function<Enum, String> SIMPLE_SHOW = new Function<Enum, String>() {
    public String fun(final Enum s) {
      return s.name();
    }
  };
  
  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value, Function<Enum, String> show) {
    for (final T t : enumClass.getEnumConstants()) {
      if (value.equals(show.fun(t))) {
        return t;
      }
    }
    return null;
  }
  public static <T extends Enum> T getEnumElementByValue(final Class<T> enumClass, final String value) {
    return getEnumElementByValue(enumClass, value, getShow(enumClass));
  }

  private static <T extends Enum> Function<Enum, String> getShow(final Class<T> enumClass) {
    return NamedEnum.class.isAssignableFrom(enumClass) ? NAMED_SHOW : SIMPLE_SHOW;
  }

  public static <T extends Enum> String getEnumValueByElement(final T element) {
    return element == null ? null : getShow(element.getClass()).fun(element);
  }

}
