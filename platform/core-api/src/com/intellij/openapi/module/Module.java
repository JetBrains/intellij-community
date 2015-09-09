/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a module in an IDEA project.
 *
 * @see ModuleManager#getModules()
 * @see ModuleComponent
 */
public interface Module extends ComponentManager, AreaInstance, Disposable {
  /**
   * The empty array of modules which cab be reused to avoid unnecessary allocations.
   */
  Module[] EMPTY_ARRAY = new Module[0];

  @NonNls String ELEMENT_TYPE = "type";

  /**
   * Returns the <code>VirtualFile</code> for the module .iml file.
   *
   * @return the virtual file instance.
   */
  @Nullable
  VirtualFile getModuleFile();

  /**
   * System-independent path to the .iml file.
   */
  @NotNull
  String getModuleFilePath();

  /**
   * Returns the project to which this module belongs.
   *
   * @return the project instance.
   */
  @NotNull Project getProject();

  /**
   * Returns the name of this module.
   *
   * @return the module name.
   */
  @NotNull String getName();

  /**
   * Checks if the module instance has been disposed and unloaded.
   *
   * @return true if the module has been disposed, false otherwise
   */
  @Override
  boolean isDisposed();

  boolean isLoaded();

  /**
   * Removes a custom option from this module.
   *
   * @param key the name of the custom option.
   */
  void clearOption(@NotNull String key);

  /**
   * Sets a custom option for this module.
   *
   * @param key the name of the custom option.
   * @param value the value of the custom option.
   */
  void setOption(@NotNull String key, @NotNull String value);

  /**
   * Gets the value of a custom option for this module.
   *
   * @param key the name of the custom option.
   * @return the value of the custom option, or null if no value has been set.
   */
  @Nullable
  String getOptionValue(@NotNull String key);

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
}
