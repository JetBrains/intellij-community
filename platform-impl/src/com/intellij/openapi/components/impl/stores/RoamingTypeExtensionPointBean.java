package com.intellij.openapi.components.impl.stores;

import com.intellij.util.xmlb.annotations.Attribute;

public class RoamingTypeExtensionPointBean {
  @Attribute("component")
  public String componentName;
  @Attribute("type")
  public String roamingType;
}
