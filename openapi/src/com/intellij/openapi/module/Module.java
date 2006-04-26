/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.pom.PomModule;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
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

  /**
   * Returns the <code>VirtualFile</code> for the module .iml file.
   *
   * @return the virtual file instance.
   */
  VirtualFile getModuleFile();

  /**
   * Returns the path to the module .iml file.
   *
   * @return the path to the .iml file.
   */
  @NotNull String getModuleFilePath();

  /**
   * Returns the type of this module.
   *
   * @return the module type.
   */
  @NotNull ModuleType getModuleType();

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
  boolean isDisposed();

  /**
   * Returns the value of the option "Use absolute/relative paths for files outside
   * the module file directory" for this module.
   *
   * @return true if relative paths are used, false if absolute paths are used.
   */
  boolean isSavePathsRelative();

  /**
   * Sets the value of the option "Use absolute/relative paths for files outside
   * the module file directory" for this module.
   *
   * @param b true if relative paths are used, false if absolute paths are used.
   */
  void setSavePathsRelative(boolean b);

  /**
   * Sets a custom option for this module.
   *
   * @param optionName the name of the custom option.
   * @param optionValue the value of the custom option.
   */
  void setOption(@NotNull String optionName, @NotNull String optionValue);

  /**
   * Gets the value of a custom option for this module.
   *
   * @param optionName the name of the custom option.
   * @return the value of the custom option, or null if no value has been set.
   */
  @Nullable String getOptionValue(@NotNull String optionName);

  /**                                             
   * Returns the {@link com.intellij.pom.PomModule} representation of this module.
   *
   * @return the POM module instance.
   */
  @NotNull PomModule getPom();

  GlobalSearchScope getModuleScope();
  GlobalSearchScope getModuleWithLibrariesScope();
  GlobalSearchScope getModuleWithDependenciesScope();
  GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests);
  GlobalSearchScope getModuleWithDependentsScope();
  GlobalSearchScope getModuleTestsWithDependentsScope();
  GlobalSearchScope getModuleRuntimeScope(boolean includeTests);

  @Nullable
  LanguageLevel getLanguageLevel();

  @NotNull
  LanguageLevel getEffectiveLanguageLevel();
}
