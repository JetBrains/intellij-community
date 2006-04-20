/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.lw;

import org.jdom.Element;
import org.jdom.Attribute;
import com.intellij.uiDesigner.UIFormXmlConstants;

import java.awt.Font;
import java.awt.Color;

public final class LwXmlReader {
  private LwXmlReader() {
  }

  /**
   * @return can be <code>null</code>.
   */
  public static Element getChild(final Element element, final String childName) {
    return element.getChild(childName, element.getNamespace());
  }

  /**
   * @return never <code>null</code>.
   */
  public static Element getRequiredChild(final Element element, final String childName) {
    final Element child = getChild(element, childName);
    if(child == null){
      throw new IllegalArgumentException("subtag '" + childName + "' is required: "+element);
    }
    return child;
  }

  /**
   * @return <code>null</code> or trimmed attribute value.
   */
  public static String getString(final Element element, final String attributeName){
    final String value = element.getAttributeValue(attributeName);
    return value != null ? value.trim() : null;
  }

  /**
   * @return never <code>null</code> trimmed attribute value.
   */
  public static String getRequiredString(final Element element, final String attributeName) {
    final String value = getString(element, attributeName);
    if(value != null){
      return value;
    }
    else{
      throw new IllegalArgumentException("attribute '" + attributeName + "' is required: "+element);
    }
  }

  public static int getRequiredInt(final Element element, final String attributeName) {
    final String str = getRequiredString(element, attributeName);
    try {
      return Integer.parseInt(str);
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("attribute '" + attributeName + "' is not a proper integer: " + str);
    }
  }

  public static int getOptionalInt(final Element element, final String attributeName, final int defaultValue) {
    final String str = element.getAttributeValue(attributeName);
    if (str == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(str);
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("attribute '" + attributeName + "' is not a proper integer: " + str);
    }
  }

  public static boolean getOptionalBoolean(final Element element, final String attributeName, final boolean defaultValue) {
    final String str = element.getAttributeValue(attributeName);
    if (str == null) {
      return defaultValue;
    }
    return Boolean.valueOf(str).booleanValue();
  }

  public static double getRequiredDouble(final Element element, final String attributeName) {
    final String str = getRequiredString(element, attributeName);
    try {
      return Double.parseDouble(str);
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("attribute '" + attributeName + "' is not a proper double: " + str);
    }
  }

  public static StringDescriptor getStringDescriptor(final Element element, final String valueAttr,
                                                     final String bundleAttr, final String keyAttr) {
    final String title = element.getAttributeValue(valueAttr);
    if (title != null) {
      StringDescriptor descriptor = StringDescriptor.create(title);
      descriptor.setNoI18n(getOptionalBoolean(element, UIFormXmlConstants.ATTRIBUTE_NOI18N, false));
      return descriptor;
    }
    else {
      final String bundle = element.getAttributeValue(bundleAttr);
      if (bundle != null) {
        final String key = getRequiredString(element, keyAttr);
        return new StringDescriptor(bundle, key);
      }
    }

    return null;
  }

  public static FontDescriptor getFontDescriptor(final Element element) {
    String fontName = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_NAME);
    if (fontName != null) {
      int fontSize = getRequiredInt(element, UIFormXmlConstants.ATTRIBUTE_SIZE);
      int fontStyle = getRequiredInt(element, UIFormXmlConstants.ATTRIBUTE_STYLE);
      return new FontDescriptor(new Font(fontName, fontStyle, fontSize));
    }
    return FontDescriptor.fromSwingFont(LwXmlReader.getRequiredString(element, UIFormXmlConstants.ATTRIBUTE_SWING_FONT));
  }

  public static ColorDescriptor getColorDescriptor(final Element element) throws Exception {
    Attribute attr = element.getAttribute(UIFormXmlConstants.ATTRIBUTE_COLOR);
    if (attr != null) {
      return new ColorDescriptor(new Color(attr.getIntValue()));
    }
    String swingColor = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_SWING_COLOR);
    if (swingColor != null) {
      return ColorDescriptor.fromSwingColor(swingColor);
    }
    String systemColor = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_SYSTEM_COLOR);
    if (systemColor != null) {
      return ColorDescriptor.fromSystemColor(systemColor);
    }
    String awtColor = element.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_AWT_COLOR);
    if (awtColor != null) {
      return ColorDescriptor.fromAWTColor(awtColor);
    }
    return new ColorDescriptor(null);
  }
}
