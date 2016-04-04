/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.Color;

/**
 * This class is intended to read a value from the value element
 * that is the element, which value is defined in the {@code value} attribute.
 * Also it may have the following platform-specific attributes:
 * {@code windows}, {@code mac}, {@code linux}.  If one of them is set,
 * it should be used instead of the default one.
 *
 * @author Sergey.Malenkov
 */
class ValueElementReader {
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String MAC = "mac";
  @NonNls private static final String LINUX = "linux";
  @NonNls private static final String WINDOWS = "windows";
  private static final String OS = SystemInfo.isWindows ? WINDOWS : SystemInfo.isMac ? MAC : LINUX;
  private static final Logger LOG = Logger.getInstance(ValueElementReader.class);

  private String myAttribute;

  /**
   * Initializes the attribute that should be checked before
   * the {@code value} attribute and before the platform-specific attributes.
   *
   * @param attribute the priority attribute
   */
  public void setAttribute(String attribute) {
    myAttribute = StringUtil.isEmpty(attribute) ? null : attribute;
  }

  /**
   * Reads a value of the specified type from the given element.
   *
   * @param type    the class that defines the result type
   * @param element the value element
   * @param <T>     the result type
   * @return a value or {@code null} if it cannot be read
   */
  public <T> T read(Class<T> type, Element element) {
    T value = null;
    if (element != null) {
      if (myAttribute != null) {
        value = read(type, element, myAttribute);
      }
      if (value == null) {
        value = read(type, element, OS);
        if (value == null) {
          value = read(type, element, VALUE);
        }
      }
    }
    return value;
  }

  /**
   * Reads a value of the specified type
   * from the specified attribute of the given element.
   *
   * @param type      the class that defines the result type
   * @param element   the value element
   * @param attribute the attribute that contains a value
   * @param <T>       the result type
   * @return a value or {@code null} if it cannot be read
   */
  private <T> T read(Class<T> type, Element element, String attribute) {
    String value = element.getAttributeValue(attribute);
    if (value != null) {
      value = value.trim();
      if (value.isEmpty()) {
        if (LOG.isDebugEnabled()) LOG.debug("empty attribute: " + attribute);
      }
      else {
        try {
          return convert(type, value);
        }
        catch (Exception exception) {
          if (LOG.isDebugEnabled()) LOG.debug("wrong attribute: " + attribute, exception);
        }
      }
    }
    return null;
  }

  /**
   * Converts a string value of the specified type
   * from the specified attribute of the given element.
   *
   * @param type  the class that defines the result type
   * @param value a string value to convert
   * @param <T>   the result type
   * @return
   */
  protected <T> T convert(Class<T> type, String value) {
    if (String.class.equals(type)) {
      //noinspection unchecked
      return (T)value;
    }
    if (Integer.class.equals(type)) {
      //noinspection unchecked
      return (T)Integer.valueOf(value);
    }
    if (Float.class.equals(type)) {
      //noinspection unchecked
      return (T)Float.valueOf(value);
    }
    if (Color.class.equals(type)) {
      //noinspection unchecked
      return (T)toColor(value);
    }
    if (Enum.class.isAssignableFrom(type)) {
      //noinspection unchecked
      return (T)(toEnum((Class<Enum>)type, value));
    }
    if (Boolean.class.equals(type)) {
      //noinspection unchecked
      return (T)Boolean.valueOf(value);
    }
    throw new IllegalArgumentException("unsupported " + type);
  }

  private static <T extends Enum> T toEnum(Class<T> type, String value) {
    for (T field : type.getEnumConstants()) {
      if (value.equalsIgnoreCase(field.name()) || value.equals(String.valueOf(field.ordinal()))) {
        return field;
      }
    }
    throw new IllegalArgumentException(value);
  }

  private static Color toColor(String value) {
    int rgb;
    try {
      rgb = Integer.parseInt(value, 16);
    }
    catch (NumberFormatException ignored) {
      rgb = Integer.decode(value);
    }
    return new Color(rgb);
  }
}
