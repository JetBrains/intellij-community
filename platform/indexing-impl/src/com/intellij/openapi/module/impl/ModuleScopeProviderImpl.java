/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.StripedLockIntObjectConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module myModule;
  private final StripedLockIntObjectConcurrentHashMap<GlobalSearchScope> myScopeCache = new StripedLockIntObjectConcurrentHashMap<GlobalSearchScope>();
  private GlobalSearchScope myModuleWithDependentsScope;
  private GlobalSearchScope myModuleTestsWithDependentsScope;

  public ModuleScopeProviderImpl(@NotNull Module module) {
    myModule = module;
  }

  @NotNull
  public GlobalSearchScope getCachedScope(@ModuleWithDependenciesScope.ScopeConstant int options) {
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
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithLibrariesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.LIBRARIES);
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE | ModuleWithDependenciesScope.TESTS | ModuleWithDependenciesScope.MODULES);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentScope() {
    return getCachedScope(ModuleWithDependenciesScope.CONTENT);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    return getCachedScope(ModuleWithDependenciesScope.CONTENT | ModuleWithDependenciesScope.MODULES);
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return getCachedScope(ModuleWithDependenciesScope.COMPILE |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependentsScope() {
    GlobalSearchScope scope = myModuleWithDependentsScope;
    if (scope == null) {
      myModuleWithDependentsScope = scope = new ModuleWithDependentsScope(myModule, false);
    }
    return scope;
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    GlobalSearchScope scope = myModuleTestsWithDependentsScope;
    if (scope == null) {
      myModuleTestsWithDependentsScope = scope = new ModuleWithDependentsScope(myModule, true);
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
  public void clearCache() {
    myScopeCache.clear();
    myModuleWithDependentsScope = null;
    myModuleTestsWithDependentsScope = null;
  }

}
