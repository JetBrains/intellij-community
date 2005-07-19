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

public final class LwXmlReader {
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
}
