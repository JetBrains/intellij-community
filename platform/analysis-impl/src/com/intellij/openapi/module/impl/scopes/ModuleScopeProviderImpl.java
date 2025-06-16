// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module module;
  private final IntObjectMap<GlobalSearchScope> scopeCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private ModuleWithDependentsTestScope moduleTestsWithDependentsScope;
  private volatile ModuleWithDependenciesContentScope moduleWithDependenciesContentScope;

  public ModuleScopeProviderImpl(@NotNull Module module) {
    this.module = module;
  }

  private @NotNull GlobalSearchScope getCachedScope(@ModuleWithDependenciesScope.ScopeConstant int options) {
    GlobalSearchScope scope = scopeCache.get(options);
    if (scope == null) {
      scope = new ModuleWithDependenciesScope(module, options);
      scopeCache.put(options, scope);
    }
    return scope;
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.LIBRARIES);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.MODULES);
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
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
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
      ModuleWithDependenciesScope.MODULES | ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleProductionSourceScope() {
    return getCachedScope(0);
  }

  @Override
  public final @NotNull GlobalSearchScope getModuleTestSourceScope() {
    return getCachedScope(ModuleWithDependenciesScope.TESTS);
  }

  @Override
  public final void clearCache() {
    scopeCache.clear();
    moduleTestsWithDependentsScope = null;
    moduleWithDependenciesContentScope = null;
  }
}
