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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.function.BiPredicate;

/**
 * Provides services to build project, modules, files or artifacts and execute Run Configuration.
 * <p>
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.NonExtendable
public abstract class ProjectTaskManager {
  public interface Result {
    @NotNull
    ProjectTaskContext getContext();

    boolean isAborted();

    boolean hasErrors();

    @ApiStatus.Experimental
    boolean anyTaskMatches(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate);
  }

  public static final ProjectTask[] EMPTY_TASKS_ARRAY = new ProjectTask[0];

  protected final @NotNull Project myProject;

  public ProjectTaskManager(@NotNull Project project) {
    myProject = project;
  }

  public static ProjectTaskManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectTaskManager.class);
  }

  public abstract Promise<Result> run(@NotNull ProjectTask projectTask);

  public abstract Promise<Result> run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask);

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   */
  public abstract Promise<Result> buildAllModules();

  /**
   * Rebuild the whole project modules from scratch.
   */
  public abstract Promise<Result> rebuildAllModules();

  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules modules to build
   */
  public abstract Promise<Result> build(Module @NotNull ... modules);

  public abstract Promise<Result> rebuild(Module @NotNull ... modules);

  /**
   * Compile a set of files.
   *
   * @param files a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   */
  public abstract Promise<Result> compile(VirtualFile @NotNull ... files);

  public abstract Promise<Result> build(ProjectModelBuildableElement @NotNull ... buildableElements);

  public abstract Promise<Result> rebuild(ProjectModelBuildableElement @NotNull ... buildableElements);

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

  //<editor-fold desc="Deprecated methods. To be removed in 2020.1">

  /**
   * @deprecated use {@link #run(ProjectTask)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback);

  /**
   * @deprecated use {@link #run(ProjectTaskContext, ProjectTask)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void run(@NotNull ProjectTaskContext context,
                           @NotNull ProjectTask projectTask,
                           @Nullable ProjectTaskNotification callback);

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   *
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #buildAllModules()}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void buildAllModules(@Nullable ProjectTaskNotification callback);

  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules  modules to build
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #build(Module[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void build(Module @NotNull [] modules, @Nullable ProjectTaskNotification callback);

  /**
   * @param modules  modules to rebuild
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #rebuild(Module[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void rebuild(Module @NotNull [] modules, @Nullable ProjectTaskNotification callback);

  /**
   * Compile a set of files.
   *
   * @param files    a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   * @param callback a notification callback, or null if no notifications needed.
   * @deprecated use {@link #compile(VirtualFile[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void compile(VirtualFile @NotNull [] files, @Nullable ProjectTaskNotification callback);

  /**
   * @deprecated use {@link #build(ProjectModelBuildableElement[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public abstract void build(ProjectModelBuildableElement @NotNull [] buildableElements, @Nullable ProjectTaskNotification callback);

  //</editor-fold>
}
