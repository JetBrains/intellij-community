// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import com.intellij.execution.runners.ExecutionEnvironment;
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
    return project.getService(ProjectTaskManager.class);
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

  public ProjectTask createModulesBuildTask(Module module, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    return createModulesBuildTask(new Module[]{module}, isIncrementalBuild, includeDependentModules, includeRuntimeDependencies);
  }

  public ProjectTask createModulesBuildTask(Module[] modules, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    return createModulesBuildTask(modules, isIncrementalBuild, includeDependentModules, includeRuntimeDependencies, true);
  }

  public abstract ProjectTask createModulesBuildTask(
    Module[] modules, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies, boolean includeTests
  );

  public abstract ProjectTask createBuildTask(boolean isIncrementalBuild, ProjectModelBuildableElement... artifacts);

  @ApiStatus.Experimental
  public abstract @Nullable ExecutionEnvironment createProjectTaskExecutionEnvironment(@NotNull ProjectTask projectTask);
}
