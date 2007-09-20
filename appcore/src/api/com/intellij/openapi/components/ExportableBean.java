package com.intellij.openapi.components;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author mike
 */
public class ExportableBean {
  @Attribute("serviceInterface")
  public String serviceInterface;
}
