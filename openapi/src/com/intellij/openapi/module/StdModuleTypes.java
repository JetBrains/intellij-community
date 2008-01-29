package com.intellij.openapi.module;

public class StdModuleTypes {
  // predefined module types
  public static ModuleType JAVA;

  private StdModuleTypes() {
  }

  static {
    JAVA = instantiate("com.intellij.openapi.module.JavaModuleType");
  }

  private static ModuleType instantiate(String className) {
    try {
      return (ModuleType)Class.forName(className).newInstance();
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
