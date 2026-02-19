// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
    throw new UnsupportedOperationException(); 
  }

  /**
   * Check if the task can be executed by the task runner.
   * This method is not used within the Platform and considered as obsolete.
   * Please use {@link ProjectTaskRunner#canRun(Project, ProjectTask, ProjectTaskContext)}.
   *
   * @param projectTask to check.
   * @return true if the task should be executed by this runner, false otherwise.
   */
  @ApiStatus.Obsolete(since = "2023.3")
  public abstract boolean canRun(@NotNull ProjectTask projectTask);

  /**
   * Check if the task can be executed by the task runner.
   * This method is not used within the Platform and considered as obsolete.
   * Please use {@link ProjectTaskRunner#canRun(Project, ProjectTask, ProjectTaskContext)}.
   *
   * @param project     to witch the task corresponds.
   * @param projectTask to check.
   * @return true if the task should be executed by this runner, false otherwise.
   */
  @ApiStatus.Obsolete(since = "2023.3")
  public boolean canRun(@SuppressWarnings("unused") @NotNull Project project, @NotNull ProjectTask projectTask) {
    return canRun(projectTask);
  }

  /**
   * Check if the task can be executed by the task runner.
   *
   * @param project     to witch the task corresponds.
   * @param projectTask to check.
   * @param context     of the task.
   * @return true if the task should be executed by this runner, false otherwise.
   */
  public boolean canRun(@NotNull Project project, @NotNull ProjectTask projectTask, @Nullable ProjectTaskContext context) {
    return canRun(project, projectTask);
  }

  /**
   * This method can be used when execution of some "Run Configuration" should be delegated to another tool.
   * E.g. delegated run of an "ApplicationConfiguration" by external tool.
   */
  public @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    return null;
  }

  @ApiStatus.Experimental
  public @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, ProjectTask @NotNull ... tasks) {
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
}
