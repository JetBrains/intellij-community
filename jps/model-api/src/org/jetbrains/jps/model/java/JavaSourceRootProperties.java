package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public class JavaSourceRootProperties extends JpsElementProperties {
  private final String myPackagePrefix;

  public JavaSourceRootProperties() {
    myPackagePrefix = "";
  }

  public JavaSourceRootProperties(String packagePrefix) {
    myPackagePrefix = packagePrefix;
  }

  public String getPackagePrefix() {
    return myPackagePrefix;
  }
}
