package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class JpsJavaDependenciesEnumerationHandler {
  public static abstract class Factory {

    @Nullable
    public abstract JpsJavaDependenciesEnumerationHandler createHandler(@NotNull Collection<JpsModule> modules);
  }
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return false;
  }

  public boolean isProductionOnTestsDependency(JpsDependencyElement element) {
    return false;
  }

  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return true;
  }

  public boolean shouldProcessDependenciesRecursively() {
    return true;
  }
}
