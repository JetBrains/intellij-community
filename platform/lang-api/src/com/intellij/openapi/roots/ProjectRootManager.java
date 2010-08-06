/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Allows to query and modify the list of root directories belonging to a project.
 */
public abstract class ProjectRootManager implements ModificationTracker {
  /**
   * Returns the project root manager instance for the specified project.
   *
   * @param project the project for which the instance is requested.
   * @return the instance.
   */
  public static ProjectRootManager getInstance(Project project) {
    return project.getComponent(ProjectRootManager.class);
  }

  /**
   * Returns the file index for the project.
   *
   * @return the file index instance.
   */
  @NotNull
  public abstract ProjectFileIndex getFileIndex();

  /**
   * Adds a listener for receiving notifications about changes in project roots.
   * @deprecated Subscribe to {@link com.intellij.ProjectTopics#PROJECT_ROOTS} messages instead
   *
   * @param listener the listener instance.
   */
  public abstract void addModuleRootListener(ModuleRootListener listener);

  /**
   * Adds a listener for receiving notifications about changes in project roots.
   * @deprecated Subscribe to {@link com.intellij.ProjectTopics#PROJECT_ROOTS} messages instead
   *
   * @param listener the listener instance.
   * @param parentDisposable object, after whose disposing the listener should be removed
   */
  public abstract void addModuleRootListener(ModuleRootListener listener, Disposable parentDisposable);

  /**
   * Removes a listener for receiving notifications about changes in project roots.
   * @deprecated Subscribe to {@link com.intellij.ProjectTopics#PROJECT_ROOTS} messages instead
   *
   * @param listener the listener instance.
   */
  public abstract void removeModuleRootListener(ModuleRootListener listener);

  /**
   * @deprecated use {@link #orderEntries()}
   */
  public abstract VirtualFile[] getFilesFromAllModules(OrderRootType type);

  /**
   * Creates new enumerator instance to process dependencies of all modules in the project. Only first level dependencies of
   * modules are processed so {@link OrderEnumerator#recursively()} option is ignored and {@link OrderEnumerator#withoutDepModules()} option is forced
   * @return new enumerator instance
   */
  @NotNull
  public abstract OrderEnumerator orderEntries();

  /**
   * Creates new enumerator instance to process dependencies of several modules in the project. Caching is not supported for this enumerator
   * @param modules modules to process
   * @return new enumerator instance
   */
  @NotNull
  public abstract OrderEnumerator orderEntries(@NotNull Collection<? extends Module> modules);

  /**
   * Unlike getContentRoots(), this includes the project base dir. Is this really necessary?
   * TODO: remove this method?
   * @return
   */
  public abstract VirtualFile[] getContentRootsFromAllModules();

  /**
   * Returns the list of content roots for all modules in the project.
   *
   * @return the list of content roots.
   */
  @NotNull
  public abstract VirtualFile[] getContentRoots();

  /**
   * Returns the list of source roots under the content roots for all modules in the project.
   *
   * @return the list of content source roots.
   */
  public abstract VirtualFile[] getContentSourceRoots();

  /**
   * Returns the instance of the JDK selected for the project.
   *
   * @return the JDK instance, or null if the name of the selected JDK does not correspond
   * to any existing JDK instance.
   */
  @Nullable
  public abstract Sdk getProjectJdk();

  /**
   * Returns the name of the JDK selected for the project.
   *
   * @return the JDK name.
   */
  public abstract String getProjectJdkName();

  /**
   * Sets the JDK to be used for the project.
   *
   * @param jdk the JDK instance.
   */
  public abstract void setProjectJdk(@Nullable Sdk jdk);

  /**
   * Sets the name of the JDK to be used for the project.
   *
   * @param name the name of the JDK.
   */
  public abstract void setProjectJdkName(String name);

  /**
   * Commits the change to the lists of roots for the specified modules.
   *
   * @param rootModels the root models ro commit.
   */
  public abstract void multiCommit(ModifiableRootModel[] rootModels);

  /**
   * Commits the change to the list of modules and the lists of roots for the specified modules.
   *
   * @param moduleModel the module model to commit.
   * @param rootModels the root models to commit.
   */
  public abstract void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels);
}
