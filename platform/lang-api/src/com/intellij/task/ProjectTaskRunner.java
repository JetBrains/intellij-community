// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link ProjectTaskRunner} provides an extension point to run any IDE tasks using {@link ProjectTaskManager} api.
 * Typical use case is delegation of common IDE activities(e.g. (re)build project) to external tools/plugins.
 * But it can be used for any other IDE activity which is described by some inheritor of {@link ProjectTask}.
 *
 * @see ProjectTaskManager
 *
 * @author Vladislav.Soroka
 */
public abstract class ProjectTaskRunner {
  public static final ExtensionPointName<ProjectTaskRunner> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskRunner");

  /**
   * Describes {@link ProjectTaskRunner} execution result.
   *
   * @see TaskRunnerResults
   */
  public interface Result {
    boolean isAborted();

    boolean hasErrors();
  }

  /**
   * The implementation should provide execution of the specified {@link ProjectTask}s by either means.
   * Only tasks which this {@link ProjectTaskRunner} {@link #canRun} will be passed.
   * It's expected that the {@link ProjectTaskRunner} will supply the returning {@link Promise} with the {@link Result} asynchronously when all tasks will be completed.
   *
   * @return promise of the execution result
   * @see #canRun
   */
  public Promise<Result> run(@NotNull Project project,
                             @NotNull ProjectTaskContext context,
                             ProjectTask @NotNull ... tasks) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    run(project, context, new ProjectTaskNotificationAdapter(promise), Arrays.asList(tasks));
    return promise;
  }

  /**
   * @return true if the task should be executed by this runner, false otherwise
   */
  public abstract boolean canRun(@NotNull ProjectTask projectTask);

  public boolean canRun(@SuppressWarnings("unused") @NotNull Project project, @NotNull ProjectTask projectTask) {
    return canRun(projectTask);
  }

  /**
   * This method can be used when execution of some "Run Configuration" should be delegated to another tool.
   * E.g. delegated run of an "ApplicationConfiguration" by external tool.
   */
  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    return null;
  }

  @ApiStatus.Experimental
  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, ProjectTask @NotNull ... tasks) {
    if (tasks.length == 0) return null;
    if (tasks.length == 1 && tasks[0] instanceof ExecuteRunConfigurationTask) {
      return createExecutionEnvironment(project, (ExecuteRunConfigurationTask)tasks[0], null);
    }
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

  //<editor-fold desc="Deprecated methods. To be removed in 2020.1">
  /**
   * @deprecated use {@link #run(Project, ProjectTaskContext, ProjectTask...)}
   */
  @Deprecated(forRemoval = true)
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    assertUnsupportedOperation(callback);
    notifyIfNeeded(run(project, context, tasks.toArray(new ProjectTask[]{})), callback);
  }

  private static final class ProjectTaskNotificationAdapter implements ProjectTaskNotification {
    private final @NotNull AsyncPromise<? super Result> myPromise;

    private ProjectTaskNotificationAdapter(@NotNull AsyncPromise<? super Result> promise) {
      myPromise = promise;
    }

    @Override
    public void finished(@SuppressWarnings("deprecation") @NotNull ProjectTaskResult taskResult) {
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

  private static void notifyIfNeeded(@NotNull Promise<? extends Result> promise, @SuppressWarnings("deprecation") @Nullable ProjectTaskNotification callback) {
    if (callback != null) {
      //noinspection deprecation
      promise
        .onSuccess(result -> callback.finished(new ProjectTaskResult(result.isAborted(), result.hasErrors() ? 1 : 0, 0)))
        .onError(throwable -> callback.finished(new ProjectTaskResult(true, 0, 0)));
    }
  }

  private void assertUnsupportedOperation(@SuppressWarnings("deprecation") @Nullable ProjectTaskNotification callback) {
    if (callback instanceof ProjectTaskNotificationAdapter) {
      throw new UnsupportedOperationException("Please, provide implementation non-deprecated ProjectTaskRunner.run(Project, ProjectTaskContext, ProjectTask...) method in " + getClass());
    }
  }
  //</editor-fold>
}
