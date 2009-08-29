package com.intellij.compiler.ant;

/**
 * @author nik
 */
public abstract class PropertyFileGenerator extends Generator {
  /**
   * Add property. Note that property name and value
   * are automatically escaped when the property file
   * is generated.
   *
   * @param name a property name
   * @param value a property value
   */
  public abstract void addProperty(String name, String value);
}
