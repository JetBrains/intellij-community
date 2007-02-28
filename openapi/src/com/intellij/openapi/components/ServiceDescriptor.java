package com.intellij.openapi.components;

import com.intellij.util.xmlb.annotations.Attribute;

public class ServiceDescriptor {
  @Attribute("serviceInterface")
  public String serviceInterface;
  @Attribute("serviceImplementation")
  public String serviceImplementation;
}
