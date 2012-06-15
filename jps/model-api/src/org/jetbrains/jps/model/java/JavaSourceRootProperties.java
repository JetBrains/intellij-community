package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public class JavaSourceRootProperties extends JpsElementProperties {
  private final String myPackagePrefix;

  public JavaSourceRootProperties() {
    myPackagePrefix = "";
  }

  public JavaSourceRootProperties(@NotNull String packagePrefix) {
    myPackagePrefix = packagePrefix;
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }
}
