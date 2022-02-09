// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.*;

import java.io.File;
import java.nio.file.Path;

/**
 * Represents a module in an IDEA project.
 *
 * @see ModuleManager#getModules()
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public interface Module extends ComponentManager, AreaInstance, Disposable {
  /**
   * The empty array of modules which can be reused to avoid unnecessary allocations.
   */
  Module[] EMPTY_ARRAY = new Module[0];

  @NonNls String ELEMENT_TYPE = "type";

  /**
   * @deprecated Module level message bus is deprecated. Please use application- or project- level.
   */
  @Override
  @Deprecated
  @NotNull MessageBus getMessageBus();

  /**
   * Returns the {@code VirtualFile} for the module {@code .iml} file. Note that location if {@code .iml} file may not be related to location of the module
   * files, it may be stored in a different directory, under {@code .idea/modules} or doesn't exist at all if the module configuration is imported
   * from external project system (e.g., Gradle). So only internal subsystems which deal with serialization are supposed to use this method.
   * If you need to find a directory (directories) where source files for the module are located, get its {@link com.intellij.openapi.roots.ModuleRootModel#getContentRoots() content roots}.
   * If you need to get just some directory near to module files (e.g., to select by default in a file chooser), use {@link com.intellij.openapi.project.ProjectUtil#guessModuleDir(Module)}.
   */
  @ApiStatus.Internal
  @Nullable VirtualFile getModuleFile();

  /**
   * Returns path to the module {@code .iml} file. This method isn't supposed to be used from plugins, see {@link #getModuleFile()} details.
   */
  @ApiStatus.Internal
  @SystemIndependent
  @NonNls
  default @NotNull String getModuleFilePath() {
    return getModuleNioFile().toString().replace(File.separatorChar, '/');
  }

  /**
   * Returns path to the module {@code .iml} file. This method isn't supposed to be used from plugins, see {@link #getModuleFile()} details.
   */
  @ApiStatus.Internal
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
  @NotNull @NlsSafe String getName();

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
  @NonNls
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

  /**
   * This method isn't supposed to be used from plugins. If you really need to determine the type of a module, use
   * {@link com.intellij.openapi.module.ModuleType#get(Module) ModuleType.get}. However, it would be better to make your functionality work regardless
   * of type of the module, see {@link com.intellij.openapi.module.ModuleType ModuleType}'s javadoc for details.
   */
  @ApiStatus.Internal
  @Nullable
  @NonNls
  default String getModuleTypeName() {
    return getOptionValue(ELEMENT_TYPE);
  }

  /**
   * This method isn't supposed to be used from plugins, module type should be passed as a parameter to {@link com.intellij.openapi.module.ModuleManager#newModule}
   * when module is created.
   */
  @ApiStatus.Internal
  default void setModuleType(@NotNull @NonNls String name) {
    setOption(ELEMENT_TYPE, name);
  }
}
