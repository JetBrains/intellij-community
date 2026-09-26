// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module module;
  private ModuleWithDependentsTestScope moduleTestsWithDependentsScope;
  private volatile ModuleWithDependenciesContentScope moduleWithDependenciesContentScope;
  private final ModuleWithDependenciesScopeCache cache;

  public ModuleScopeProviderImpl(@NotNull Module module) {
    this.module = module;
    cache = module.getProject().getService(ModuleWithDependenciesScopeCache.class);
  }

  private @NotNull GlobalSearchScope getCachedScope(@ScopeConstant int options) {
    return cache.getCachedScope(module, options);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleScope() {
    return getCachedScope(ModuleScopeUtil.COMPILE_ONLY | ModuleScopeUtil.TESTS);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleScopeUtil.COMPILE_ONLY | (includeTests ? ModuleScopeUtil.TESTS : 0));
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleScopeUtil.COMPILE_ONLY | ModuleScopeUtil.TESTS | ModuleScopeUtil.LIBRARIES);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleScopeUtil.COMPILE_ONLY | ModuleScopeUtil.TESTS | ModuleScopeUtil.MODULES);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleContentScope() {
    return new ModuleContentScope(module);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleContentWithDependenciesScope() {
    ModuleWithDependenciesContentScope scope = moduleWithDependenciesContentScope;
    if (scope == null) {
      scope = new ModuleWithDependenciesContentScope(module);
      moduleWithDependenciesContentScope = scope;
    }
    return scope;
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return getCachedScope(ModuleScopeUtil.COMPILE_ONLY |
                          ModuleScopeUtil.MODULES |
                          ModuleScopeUtil.LIBRARIES | (includeTests ? ModuleScopeUtil.TESTS : 0));
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependentsScope() {
    return ((ModuleWithDependentsTestScope)getModuleTestsWithDependentsScope()).getDelegate();
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleTestsWithDependentsScope() {
    ModuleWithDependentsTestScope scope = moduleTestsWithDependentsScope;
    if (scope == null) {
      scope = new ModuleWithDependentsTestScope(module);
      moduleTestsWithDependentsScope = scope;
    }
    return scope;
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return getCachedScope(
      ModuleScopeUtil.MODULES | ModuleScopeUtil.LIBRARIES | (includeTests ? ModuleScopeUtil.TESTS : 0));
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleProductionSourceScope() {
    return getCachedScope(0);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleTestSourceScope() {
    return getCachedScope(ModuleScopeUtil.TESTS);
  }

  @Override
  public final void clearCache() {
    // ModuleWithDependenciesScopeCache cache is cleared via com.intellij.openapi.roots.impl.ProjectRootManagerComponent.clearScopesCachesForModules
    moduleTestsWithDependentsScope = null;
    moduleWithDependenciesContentScope = null;
  }
}
