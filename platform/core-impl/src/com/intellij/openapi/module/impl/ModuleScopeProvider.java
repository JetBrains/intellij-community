/*
 * Copyright 2000-2012 JetBrains s.r.o.
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


import com.intellij.psi.search.GlobalSearchScope;

/**
 * Author: dmitrylomov
 */
public interface ModuleScopeProvider {
  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @return scope including sources and tests, excluding libraries and dependencies.
   */
  GlobalSearchScope getModuleScope();

  GlobalSearchScope getModuleScope(boolean includeTests);

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @return scope including sources, tests, and libraries, excluding dependencies.
   */
  GlobalSearchScope getModuleWithLibrariesScope();

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @return scope including sources, tests, and dependencies, excluding libraries.
   */
  GlobalSearchScope getModuleWithDependenciesScope();

  GlobalSearchScope getModuleContentScope();
  GlobalSearchScope getModuleContentWithDependenciesScope();

  GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests);
  GlobalSearchScope getModuleWithDependentsScope();
  GlobalSearchScope getModuleTestsWithDependentsScope();
  GlobalSearchScope getModuleRuntimeScope(boolean includeTests);

  void clearCache();
}
