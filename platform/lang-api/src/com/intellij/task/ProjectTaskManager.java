/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services to build project, modules, files or artifacts and execute Run Configuration.
 * <p>
 *
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class ProjectTaskManager {
  protected final @NotNull Project myProject;

  public ProjectTaskManager(@NotNull Project project) {
    myProject = project;
  }

  public static ProjectTaskManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectTaskManager.class);
  }

  public abstract void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback);

  public abstract void run(@NotNull ProjectTaskContext context,
                           @NotNull ProjectTask projectTask,
                           @Nullable ProjectTaskNotification callback);

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   *
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void buildAllModules(@Nullable ProjectTaskNotification callback);

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   */
  public void buildAllModules() {
    buildAllModules(null);
  }

  /**
   * Rebuild the whole project modules from scratch.
   *
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void rebuildAllModules(@Nullable ProjectTaskNotification callback);

  /**
   * Rebuild the whole project modules from scratch.
   */
  public void rebuildAllModules() {
    rebuildAllModules(null);
  }

  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules  modules to build
   * @param callback a notification callback, or null if no notifications needed
   */
  public abstract void build(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback);

  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules modules to build
   */
  public void build(@NotNull Module... modules) {
    build(modules, null);
  }

  public abstract void rebuild(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback);

  public void rebuild(@NotNull Module... modules) {
    rebuild(modules, null);
  }

  /**
   * Compile a set of files.
   *
   * @param files    a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   * @param callback a notification callback, or null if no notifications needed.
   */
  public abstract void compile(@NotNull VirtualFile[] files, @Nullable ProjectTaskNotification callback);

  /**
   * Compile a set of files.
   *
   * @param files a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   */
  public void compile(@NotNull VirtualFile... files) {
    compile(files, null);
  }

  public abstract void build(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback);

  public void build(@NotNull ProjectModelBuildableElement... buildableElements) {
    build(buildableElements, null);
  }

  public abstract void rebuild(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback);

  public void rebuild(@NotNull ProjectModelBuildableElement... buildableElements) {
    rebuild(buildableElements, null);
  }

  public abstract ProjectTask createAllModulesBuildTask(boolean isIncrementalBuild, Project project);

  public abstract ProjectTask createModulesBuildTask(Module module,
                                                     boolean isIncrementalBuild,
                                                     boolean includeDependentModules,
                                                     boolean includeRuntimeDependencies);

  public abstract ProjectTask createModulesBuildTask(Module[] modules,
                                                     boolean isIncrementalBuild,
                                                     boolean includeDependentModules,
                                                     boolean includeRuntimeDependencies);

  public abstract ProjectTask createBuildTask(boolean isIncrementalBuild, ProjectModelBuildableElement... artifacts);
}
