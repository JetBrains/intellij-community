// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;


import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
@ApiStatus.Internal
public interface ModuleScopeProvider {
  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @return scope including sources and tests, excluding libraries and dependencies.
   */
  @NotNull
  GlobalSearchScope getModuleScope();

  @NotNull
  GlobalSearchScope getModuleScope(boolean includeTests);

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @return scope including sources, tests, and libraries, excluding dependencies.
   */
  @NotNull
  GlobalSearchScope getModuleWithLibrariesScope();

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @return scope including sources, tests, and dependencies, excluding libraries.
   */
  @NotNull
  GlobalSearchScope getModuleWithDependenciesScope();

  @NotNull
  GlobalSearchScope getModuleContentScope();

  @NotNull
  GlobalSearchScope getModuleContentWithDependenciesScope();

  @NotNull
  GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests);

  @NotNull
  GlobalSearchScope getModuleWithDependentsScope();

  @NotNull
  GlobalSearchScope getModuleTestsWithDependentsScope();

  @NotNull
  GlobalSearchScope getModuleRuntimeScope(boolean includeTests);

  @NotNull
  GlobalSearchScope getModuleProductionSourceScope();

  @NotNull
  GlobalSearchScope getModuleTestSourceScope();

  void clearCache();
}
