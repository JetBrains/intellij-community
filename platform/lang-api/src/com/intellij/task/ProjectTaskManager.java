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
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.function.BiPredicate;

/**
 * Provides services to build project, modules, files or artifacts and execute Run Configuration.
 * <p>
 *
 * @author Vladislav.Soroka
 */
@SuppressWarnings({"DeprecatedIsStillUsed", "deprecation"})
public abstract class ProjectTaskManager {
  public interface Result {
    @NotNull
    ProjectTaskContext getContext();

    boolean isAborted();

    boolean hasErrors();

    @ApiStatus.Experimental
    boolean contains(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate);
  }

  public static final ProjectTask[] EMPTY_TASKS_ARRAY = new ProjectTask[0];

  protected final @NotNull Project myProject;

  public ProjectTaskManager(@NotNull Project project) {
    myProject = project;
  }

  public static ProjectTaskManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectTaskManager.class);
  }

  public Promise<Result> run(@NotNull ProjectTask projectTask) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    run(projectTask, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  public Promise<Result> run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    run(context, projectTask, new ProjectTaskNotificationAdapter(promise, context));
    return promise;
  }

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   */
  public Promise<Result> buildAllModules() {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    buildAllModules(new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  /**
   * Rebuild the whole project modules from scratch.
   */
  public Promise<Result> rebuildAllModules() {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    rebuildAllModules(new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules modules to build
   */
  public Promise<Result> build(@NotNull Module... modules) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    build(modules, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  public Promise<Result> rebuild(@NotNull Module... modules) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    rebuild(modules, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  /**
   * Compile a set of files.
   *
   * @param files a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   */
  public Promise<Result> compile(@NotNull VirtualFile... files) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    compile(files, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  public Promise<Result> build(@NotNull ProjectModelBuildableElement... buildableElements) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    build(buildableElements, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
  }

  public Promise<Result> rebuild(@NotNull ProjectModelBuildableElement... buildableElements) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    rebuild(buildableElements, new ProjectTaskNotificationAdapter(promise, new ProjectTaskContext()));
    return promise;
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


  // ********** deprecated methods ********** //

  /**
   * @deprecated use {@link #run(ProjectTask)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(run(projectTask), callback);
  }

  /**
   * @deprecated use {@link #run(ProjectTaskContext, ProjectTask)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void run(@NotNull ProjectTaskContext context,
                  @NotNull ProjectTask projectTask,
                  @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(run(context, projectTask), callback);
  }

  /**
   * Build all modules with modified files and all modules with files that depend on them all over the project.
   *
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #buildAllModules()}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void buildAllModules(@Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(buildAllModules(), callback);
  }

  /**
   * Rebuild the whole project modules from scratch.
   *
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #rebuildAllModules()}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void rebuildAllModules(@Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(rebuildAllModules(), callback);
  }


  /**
   * Build modules and all modules these modules depend on recursively.
   *
   * @param modules  modules to build
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #build(Module[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void build(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(build(modules), callback);
  }

  /**
   * @param modules  modules to rebuild
   * @param callback a notification callback, or null if no notifications needed
   * @deprecated use {@link #rebuild(Module[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void rebuild(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(rebuild(modules), callback);
  }

  /**
   * Compile a set of files.
   *
   * @param files    a list of files to compile. If a VirtualFile is a directory, all containing files should be processed.
   * @param callback a notification callback, or null if no notifications needed.
   * @deprecated use {@link #compile(VirtualFile[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void compile(@NotNull VirtualFile[] files, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(compile(files), callback);
  }

  /**
   * @deprecated use {@link #build(ProjectModelBuildableElement[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void build(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(build(buildableElements), callback);
  }

  /**
   * @deprecated use {@link #rebuild(ProjectModelBuildableElement[])}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void rebuild(@NotNull ProjectModelBuildableElement[] buildableElements, @Nullable ProjectTaskNotification callback) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(rebuild(buildableElements), callback);
  }

  private static void notifyIfNeeded(@NotNull Promise<Result> promise, @Nullable ProjectTaskNotification callback) {
    if (callback != null) {
      promise
        .onSuccess(result -> callback.finished(result.getContext(), new ProjectTaskResult(result.isAborted(), result.hasErrors() ? 1 : 0, 0)))
        .onError(throwable -> callback.finished(new ProjectTaskContext(), new ProjectTaskResult(true, 0, 0)));
    }
  }

  private static void assertUnsupportedOperation(@Nullable ProjectTaskNotification callback) {
    if (callback instanceof ProjectTaskNotificationAdapter) {
      throw new UnsupportedOperationException("Please, provide implementation for non-deprecated methods");
    }
  }

  private static class ProjectTaskNotificationAdapter implements ProjectTaskNotification {
    private final AsyncPromise<Result> myPromise;
    private final ProjectTaskContext myContext;

    private ProjectTaskNotificationAdapter(@NotNull AsyncPromise<Result> promise, @NotNull ProjectTaskContext context) {
      myPromise = promise;
      myContext = context;
    }

    @Override
    public void finished(@NotNull ProjectTaskResult executionResult) {
      myPromise.setResult(new Result() {
        @NotNull
        @Override
        public ProjectTaskContext getContext() {
          return myContext;
        }

        @Override
        public boolean isAborted() {
          return executionResult.isAborted();
        }

        @Override
        public boolean hasErrors() {
          return executionResult.getErrors() > 0;
        }

        @Override
        public boolean contains(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate) {
          return executionResult.anyMatch(predicate);
        }
      });
    }
  }
}
