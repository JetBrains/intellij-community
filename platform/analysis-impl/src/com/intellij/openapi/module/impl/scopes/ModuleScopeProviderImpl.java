// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module myModule;
  private final IntObjectMap<GlobalSearchScope> myScopeCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private ModuleWithDependentsTestScope myModuleTestsWithDependentsScope;
  private volatile ModuleWithDependenciesContentScope myModuleWithDependenciesContentScope;

  public ModuleScopeProviderImpl(@NotNull Module module) {
    myModule = module;
  }

  @NotNull
  private GlobalSearchScope getCachedScope(@ModuleWithDependenciesScope.ScopeConstant int options) {
    GlobalSearchScope scope = myScopeCache.get(options);
    if (scope == null) {
      scope = new ModuleWithDependenciesScope(myModule, options);
      myScopeCache.put(options, scope);
    }
    return scope;
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.LIBRARIES);
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.MODULES);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentScope() {
    return new ModuleContentScope(myModule);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    ModuleWithDependenciesContentScope scope = myModuleWithDependenciesContentScope;
    if (scope == null) {
      myModuleWithDependenciesContentScope = scope = new ModuleWithDependenciesContentScope(myModule);
    }
    return scope;
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependentsScope() {
    return getModuleTestsWithDependentsScope().getDelegate();
  }

  @Override
  @NotNull
  public ModuleWithDependentsTestScope getModuleTestsWithDependentsScope() {
    ModuleWithDependentsTestScope scope = myModuleTestsWithDependentsScope;
    if (scope == null) {
      myModuleTestsWithDependentsScope = scope = new ModuleWithDependentsTestScope(myModule);
    }
    return scope;
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
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
