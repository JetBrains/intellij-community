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
