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

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public abstract class ProjectTaskRunner {
  public static final ExtensionPointName<ProjectTaskRunner> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskRunner");

  public interface Result {
    boolean isAborted();

    boolean hasErrors();
  }

  public Promise<Result> run(@NotNull Project project,
                             @NotNull ProjectTaskContext context,
                             @NotNull Collection<? extends ProjectTask> tasks) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    run(project, context, new ProjectTaskNotificationAdapter(promise), tasks);
    return promise;
  }

  public Promise<Result> run(@NotNull Project project,
                             @NotNull ProjectTaskContext context,
                             @NotNull ProjectTask... tasks) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    run(project, context, new ProjectTaskNotificationAdapter(promise), tasks);
    return promise;
  }

  public abstract boolean canRun(@NotNull ProjectTask projectTask);

  public boolean canRun(@SuppressWarnings("unused") @NotNull Project project, @NotNull ProjectTask projectTask) {
    return canRun(projectTask);
  }

  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    return null;
  }

  /**
   * The flag indicates if the {@link ProjectTaskRunner} supports reporting an information about generated files during execution or not.
   * The fine-grained events per generated files allow greatly improve IDE performance for some activities like fast hotswap reload after incremental compilation.
   * <p/>
   * The support means responsibility to send {@link ProjectTaskContext#fileGenerated} events per each generated file
   * or at least supply effective output roots containing generated files using the {@link ProjectTaskContext#addDirtyOutputPathsProvider} method
   * if per-file events are not possible.
   *
   * @return true if the {@link ProjectTaskRunner} supports reporting an information about generated files during this runner tasks execution, false otherwise
   */
  @ApiStatus.Experimental
  public boolean isFileGeneratedEventsSupported() {
    return false;
  }

  /**
   * @deprecated use {@link #run(Project, ProjectTaskContext, Collection)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(run(project, context, tasks), callback);
  }

  /**
   * @deprecated use {@link #run(Project, ProjectTaskContext, ProjectTask...)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull ProjectTask... tasks) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(run(project, context, tasks), callback);
  }

  @SuppressWarnings("deprecation")
  private static class ProjectTaskNotificationAdapter implements ProjectTaskNotification {
    private final AsyncPromise<Result> myPromise;

    private ProjectTaskNotificationAdapter(@NotNull AsyncPromise<Result> promise) {
      myPromise = promise;
    }

    @Override
    public void finished(@NotNull ProjectTaskResult taskResult) {
      myPromise.setResult(new Result() {
        @Override
        public boolean isAborted() {
          return taskResult.isAborted();
        }

        @Override
        public boolean hasErrors() {
          return taskResult.getErrors() > 0;
        }
      });
    }
  }

  @SuppressWarnings("deprecation")
  private static void notifyIfNeeded(@NotNull Promise<Result> promise, @Nullable ProjectTaskNotification callback) {
    if (callback != null) {
      promise
        .onSuccess(result -> callback.finished(new ProjectTaskResult(result.isAborted(), result.hasErrors() ? 1 : 0, 0)))
        .onError(throwable -> callback.finished(new ProjectTaskResult(true, 0, 0)));
    }
  }

  @SuppressWarnings("deprecation")
  private static void assertUnsupportedOperation(@Nullable ProjectTaskNotification callback) {
    if (callback instanceof ProjectTaskNotificationAdapter) {
      throw new UnsupportedOperationException("Please, provide implementation for non-deprecated methods");
    }
  }
}
