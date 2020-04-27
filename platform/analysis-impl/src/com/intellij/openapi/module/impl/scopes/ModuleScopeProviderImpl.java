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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
public class ModuleScopeProviderImpl implements ModuleScopeProvider {
  private final Module myModule;
  private final IntObjectMap<GlobalSearchScope> myScopeCache = ContainerUtil.createConcurrentIntObjectMap();
  private ModuleWithDependentsTestScope myModuleTestsWithDependentsScope;

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
    return getCachedScope(ModuleWithDependenciesScope.COMPILE_ONLY |
                          ModuleWithDependenciesScope.MODULES |
                          ModuleWithDependenciesScope.LIBRARIES | (includeTests ? ModuleWithDependenciesScope.TESTS : 0));
  }

  @Override
  @NotNull
  public GlobalSearchScope getModuleWithDependentsScope() {
    return getModuleTestsWithDependentsScope().getBaseScope();
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
  public void clearCache() {
    myScopeCache.clear();
    myModuleTestsWithDependentsScope = null;
  }
}
