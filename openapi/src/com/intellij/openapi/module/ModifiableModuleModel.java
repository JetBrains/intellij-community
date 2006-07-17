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

import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents the model for the list of modules in a project, or a temporary copy
 * of that model displayed in the configuration UI.
 *
 * @see ModuleManager#getModifiableModel()
 */
public interface ModifiableModuleModel {
  /**
   * Returns the list of all modules in the project. Same as {@link ModuleManager#getModules()}.
   *
   * @return the array of modules.
   */
  @NotNull Module[] getModules();

  /**
   * Creates a Java module at the specified path and adds it to the project
   * to which the module manager is related. {@link #commit()} must be called to
   * bring the changes in effect.
   *
   * @param filePath the path at which the module is created.
   * @return the module instance.
   * @throws LoadCancelledException in case of internal error while creating the module.
   */
  @NotNull Module newModule(@NotNull String filePath) throws LoadCancelledException;

  /**
   * Creates a module of the specified type at the specified path and adds it to the project
   * to which the module manager is related. {@link #commit()} must be called to
   * bring the changes in effect.
   *
   * @param filePath the path at which the module is created.
   * @param moduleType the type of the module to create.
   * @return the module instance.
   * @throws LoadCancelledException in case of internal error while creating the module.
   */
  @NotNull Module newModule(@NotNull String filePath, @NotNull ModuleType moduleType) throws LoadCancelledException;

  /**
   * Loads a module from an .iml file with the specified path and adds it to the project.
   * {@link #commit()} must be called to bring the changes in effect.
   *
   * @param filePath the path to load the module from.
   * @return the module instance.
   * @throws InvalidDataException if the data in the .iml file is semantically incorrect.
   * @throws IOException if an I/O error occurred when loading the module file.
   * @throws JDOMException if the file contains invalid XML data.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   * @throws LoadCancelledException if loading the module was cancelled by some of the components.
   */
  @NotNull Module loadModule(@NotNull String filePath) throws InvalidDataException, IOException, JDOMException, ModuleWithNameAlreadyExists, LoadCancelledException;

  /**
   * Disposes of the specified module and removes it from the project. {@link #commit()}
   * must be called to bring the changes in effect.
   *
   * @param module the module to remove.
   */
  void disposeModule(@NotNull Module module);

  /**
   * Returns the project module with the specified name.
   *
   * @param name the name of the module to find.
   * @return the module instance, or null if no module with such name exists.
   */
  @Nullable Module findModuleByName(@NotNull String name);

  /**
   * Disposes of all modules in the project.
   */
  void dispose();

  /**
   * Checks if there are any uncommitted changes to the model.
   *
   * @return true if there are uncommitted changes, false otherwise
   */
  boolean isChanged();

  /**
   * Commits changes made in this model to the actual project structure.
   *
   * @throws ModuleCircularDependencyException never actually thrown (circular module dependency is not an error).
   */
  void commit() throws ModuleCircularDependencyException;

  /**
   * @deprecated use {@link #commit()} instead.
   */
  void commitAssertingNoCircularDependency();

  /**
   * Schedules the rename of a module to be performed when the model is committed.
   *
   * @param module the module to rename.
   * @param newName the new name to rename the module to.
   * @throws ModuleWithNameAlreadyExists if a module with such a name already exists in the project.
   */
  void renameModule(@NotNull Module module, @NotNull String newName) throws ModuleWithNameAlreadyExists;

  /**
   * Returns the project module which has been renamed to the specified name.
   *
   * @param newName the name of the renamed module to find.
   * @return the module instance, or null if no module has been renamed to such a name.
   */
  @Nullable Module getModuleToBeRenamed(@NotNull String newName);

  /**
   * Returns the name to which the specified module has been renamed.
   *
   * @param module the module for which the new name is requested.
   * @return the new name, or null if the module has not been renamed.
   */
  @Nullable String getNewName(@NotNull Module module);

  String[] getModuleGroupPath(Module module);

  void setModuleGroupPath(Module module, String[] groupPath);
}
