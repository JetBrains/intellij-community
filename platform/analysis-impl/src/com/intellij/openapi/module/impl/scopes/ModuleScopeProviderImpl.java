// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
@ApiStatus.Internal
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module myModule;
  private final IntObjectMap<GlobalSearchScope> myScopeCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private ModuleWithDependentsTestScope myModuleTestsWithDependentsScope;
  private volatile ModuleWithDependenciesContentScope myModuleWithDependenciesContentScope;

  public ModuleScopeProviderImpl(@NotNull Module module) {
    myModule = module;
  }

  private @NotNull GlobalSearchScope getCachedScope(@ModuleWithDependenciesScope.ScopeConstant int options) {
    GlobalSearchScope scope = myScopeCache.get(options);
    if (scope == null) {
      scope = new ModuleWithDependenciesScope(myModule, options);
      myScopeCache.put(options, scope);
    }
    return scope;
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.LIBRARIES);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.MODULES);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentScope() {
    return new ModuleContentScope(myModule);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentWithDependenciesScope() {
    ModuleWithDependenciesContentScope scope = myModuleWithDependenciesContentScope;
    if (scope == null) {
      myModuleWithDependenciesContentScope = scope = new ModuleWithDependenciesContentScope(myModule);
    }
    return scope;
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependentsScope() {
    return getModuleTestsWithDependentsScope().getDelegate();
  }

  @Override
  public @NotNull ModuleWithDependentsTestScope getModuleTestsWithDependentsScope() {
    ModuleWithDependentsTestScope scope = myModuleTestsWithDependentsScope;
    if (scope == null) {
      myModuleTestsWithDependentsScope = scope = new ModuleWithDependentsTestScope(myModule);
    }
    return scope;
  }

  @Override
  public @NotNull GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return getCachedScope(
      ModuleWithDependenciesScope.MODULES | ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  public @NotNull GlobalSearchScope getModuleProductionSourceScope() {
    return getCachedScope(0);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleTestSourceScope() {
    return getCachedScope(ModuleWithDependenciesScope.TESTS);
  }

  @Override
  public void clearCache() {
    myScopeCache.clear();
    myModuleTestsWithDependentsScope = null;
    myModuleWithDependenciesContentScope = null;
  }
}
