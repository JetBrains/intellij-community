// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.file.Path;

/**
 * Represents a module in an IDEA project.
 *
 * @see ModuleManager#getModules()
 * @see ModuleComponent
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public interface Module extends ComponentManager, AreaInstance, Disposable {
  /**
   * The empty array of modules which cab be reused to avoid unnecessary allocations.
   */
  Module[] EMPTY_ARRAY = new Module[0];

  @NonNls String ELEMENT_TYPE = "type";

  /**
   * Returns the {@code VirtualFile} for the module .iml file.
   *
   * @return the virtual file instance.
   */
  @Nullable
  VirtualFile getModuleFile();

  @SystemIndependent
  default @NotNull String getModuleFilePath() {
    return getModuleNioFile().toString().replace(File.separatorChar, '/');
  }

  @NotNull Path getModuleNioFile();

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
   * @deprecated Please store options in your own {@link com.intellij.openapi.components.PersistentStateComponent}
   */
  @Deprecated
  default void clearOption(@NotNull String key) {
    setOption(key, null);
  }

  /**
   * @deprecated Please store options in your own {@link com.intellij.openapi.components.PersistentStateComponent}
   */
  @Deprecated
  void setOption(@NotNull String key, @Nullable String value);

  /**
   * @deprecated Please store options in your own {@link com.intellij.openapi.components.PersistentStateComponent}
   */
  @Deprecated
  @Nullable
  String getOptionValue(@NotNull String key);

  /**
   * @return module scope including source and tests, excluding libraries and dependencies.
   */
  @NotNull
  GlobalSearchScope getModuleScope();

  /**
   * @param includeTests whether to include test source
   * @return module scope including source and, optionally, tests, excluding libraries and dependencies.
   */
  @NotNull
  GlobalSearchScope getModuleScope(boolean includeTests);

  /**
   * @return module scope including source, tests, and libraries, excluding dependencies.
   */
  @NotNull
  GlobalSearchScope getModuleWithLibrariesScope();

  /**
   * @return module scope including source, tests, and dependencies, excluding libraries.
   */
  @NotNull
  GlobalSearchScope getModuleWithDependenciesScope();

  /**
   * @return a scope that includes everything in module content roots, without any dependencies or libraries
   */
  @NotNull
  GlobalSearchScope getModuleContentScope();

  /**
   * @return a scope that includes everything under the content roots of this module and its dependencies, with test source
   */
  @NotNull
  GlobalSearchScope getModuleContentWithDependenciesScope();

  /**
   * @param includeTests whether test source and test dependencies should be included
   * @return a scope including module source and dependencies with libraries
   */
  @NotNull
  GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests);

  /**
   * @return a scope including everything under the content roots of this module and all modules that depend on it, directly or indirectly (via exported dependencies), excluding test source and resources
   */
  @NotNull
  GlobalSearchScope getModuleWithDependentsScope();

  /**
   * @return same as {@link #getModuleWithDependentsScope()}, but with test source/resources included
   */
  @NotNull
  GlobalSearchScope getModuleTestsWithDependentsScope();

  /**
   * @param includeTests whether test source and test dependencies should be included
   * @return a scope including production (and optionally test) source of this module and all modules and libraries it depends upon, including runtime and transitive dependencies, even if they're not exported.
   */
  @NotNull
  GlobalSearchScope getModuleRuntimeScope(boolean includeTests);

  @Nullable
  default String getModuleTypeName() {
    //noinspection deprecation
    return getOptionValue(ELEMENT_TYPE);
  }

  default void setModuleType(@NotNull String name) {
    //noinspection deprecation
    setOption(ELEMENT_TYPE, name);
  }
}
