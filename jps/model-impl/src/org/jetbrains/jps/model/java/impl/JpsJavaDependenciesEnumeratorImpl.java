// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsSdkDependency;
import org.jetbrains.jps.model.module.impl.JpsDependenciesEnumeratorBase;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class JpsJavaDependenciesEnumeratorImpl extends JpsDependenciesEnumeratorBase<JpsJavaDependenciesEnumeratorImpl> implements JpsJavaDependenciesEnumerator {
  private boolean myProductionOnly;
  private boolean myRuntimeOnly;
  private boolean myCompileOnly;
  private boolean myExportedOnly;
  private boolean myRecursivelyExportedOnly;
  private JpsJavaClasspathKind myClasspathKind;
  private final List<JpsJavaDependenciesEnumerationHandler> myHandlers;

  public JpsJavaDependenciesEnumeratorImpl(Collection<JpsModule> rootModules) {
    super(rootModules);
    List<JpsJavaDependenciesEnumerationHandler> handlers = JpsJavaDependenciesEnumerationHandler.createHandlers(rootModules);
    myHandlers = handlers != null ? handlers : Collections.emptyList();
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator exportedOnly() {
    if (myRecursively) {
      myRecursivelyExportedOnly = true;
    }
    else {
      myExportedOnly = true;
    }
    return this;
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator recursivelyExportedOnly() {
    return recursively().exportedOnly();
  }

  @Override
  public @NotNull JpsJavaDependenciesEnumerator includedIn(@NotNull JpsJavaClasspathKind classpathKind) {
    myClasspathKind = classpathKind;
    return this;
  }

  @Override
  public @NotNull JpsJavaDependenciesRootsEnumerator classes() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.COMPILED);
  }

  @Override
  public @NotNull JpsJavaDependenciesRootsEnumerator sources() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.SOURCES);
  }

  @Override
  public @NotNull JpsJavaDependenciesRootsEnumerator annotations() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsAnnotationRootType.INSTANCE);
  }

  @Override
  protected JpsJavaDependenciesEnumeratorImpl self() {
    return this;
  }

  @Override
  protected boolean shouldProcessDependenciesRecursively() {
    return JpsJavaDependenciesEnumerationHandler.shouldProcessDependenciesRecursively(myHandlers);
  }

  @Override
  protected boolean shouldProcess(JpsModule module, JpsDependencyElement element) {
    boolean exported = !(element instanceof JpsSdkDependency);
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(element);
    if (extension != null) {
      exported = extension.isExported();
      JpsJavaDependencyScope scope = extension.getScope();
      boolean forTestCompile = scope.isIncludedIn(JpsJavaClasspathKind.TEST_COMPILE) || scope == JpsJavaDependencyScope.RUNTIME &&
                                                                                        shouldAddRuntimeDependenciesToTestCompilationClasspath();
      if (myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !forTestCompile
        || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) && !scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)
        || myClasspathKind != null && !scope.isIncludedIn(myClasspathKind) && !(myClasspathKind == JpsJavaClasspathKind.TEST_COMPILE && forTestCompile)) {
        return false;
      }
      if (myProductionOnly) {
        if (!scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
            || myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)
            || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
          return false;
        }
      }
    }
    if (!exported) {
      if (myExportedOnly) return false;
      if ((myRecursivelyExportedOnly || element instanceof JpsSdkDependency) && !isEnumerationRootModule(module)) return false;
    }
    return true;
  }

  public boolean isProductionOnly() {
    return myProductionOnly || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_RUNTIME || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_COMPILE;
  }

  public boolean isProductionOnTests(JpsDependencyElement element) {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (handler.isProductionOnTestsDependency(element)) {
        return true;
      }
    }
    return false;
  }

  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (!handler.shouldIncludeTestsFromDependentModulesToTestClasspath()) {
        return false;
      }
    }
    return true;
  }

  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    for (JpsJavaDependenciesEnumerationHandler handler : myHandlers) {
      if (handler.shouldAddRuntimeDependenciesToTestCompilationClasspath()) {
        return true;
      }
    }
    return false;
  }
}
