package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class JavaSourceRootProperties {
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
