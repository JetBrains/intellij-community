// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;
import java.util.List;

/**
 * Implement this class and register implementations of its {@link Factory} in META-INF/services/org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler$Factory
 * file to change how dependencies of modules are processed. The same logic must be implemented in {@link com.intellij.openapi.roots.OrderEnumerationHandler}
 * extension on IDE side.
 */
public abstract class JpsJavaDependenciesEnumerationHandler {
  public static List<JpsJavaDependenciesEnumerationHandler> createHandlers(Collection<JpsModule> rootModules) {
    List<JpsJavaDependenciesEnumerationHandler> handlers = null;
    for (Factory factory : JpsServiceManager.getInstance().getExtensions(Factory.class)) {
      JpsJavaDependenciesEnumerationHandler handler = factory.createHandler(rootModules);
      if (handler != null) {
        if (handlers == null) {
          handlers = new SmartList<>();
        }
        handlers.add(handler);
      }
    }
    return handlers;
  }

  public static boolean shouldProcessDependenciesRecursively(final List<? extends JpsJavaDependenciesEnumerationHandler> handlers) {
    if (handlers != null) {
      for (JpsJavaDependenciesEnumerationHandler handler : handlers) {
        if (!handler.shouldProcessDependenciesRecursively()) {
          return false;
        }
      }
    }
    return true;
  }

  public abstract static class Factory {

    public abstract @Nullable JpsJavaDependenciesEnumerationHandler createHandler(@NotNull Collection<JpsModule> modules);
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
