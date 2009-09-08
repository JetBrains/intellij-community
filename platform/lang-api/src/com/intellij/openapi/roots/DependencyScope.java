package com.intellij.openapi.roots;

import org.jdom.Element;

/**
 * @author yole
 */
public enum DependencyScope {
  COMPILE {
    @Override
    public String toString() {
      return "Compile";
    }},
  TEST {
    @Override
    public String toString() {
      return "Test";
    }},
  RUNTIME {
    @Override
    public String toString() {
      return "Runtime";
    }},
  PROVIDED {
    @Override
    public String toString() {
      return "Provided";
    }};

  private static final String SCOPE_ATTR = "scope";

  public static DependencyScope readExternal(Element element) {
    String scope = element.getAttributeValue(SCOPE_ATTR);
    if (scope != null) {
      try {
        return valueOf(scope);
      }
      catch (IllegalArgumentException e) {
        return COMPILE;
      }
    }
    else {
      return COMPILE;
    }
  }

  public void writeExternal(Element element) {
    if (this != COMPILE) {
      element.setAttribute(SCOPE_ATTR, name());
    }
  }
}
