// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/// [ProjectTaskRunner] provides an extension point to run any IDE tasks using [ProjectTaskManager] API.
/// A typical use case is delegation of common IDE activities (e.g., (re)build project) to external tools/plugins.
/// But it can be used for any other IDE activity described by some inheritor of [ProjectTask].
///
/// @see ProjectTaskManager
public abstract class ProjectTaskRunner {
  public static final ExtensionPointName<ProjectTaskRunner> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskRunner");

  /// Describes a [ProjectTaskRunner] execution result.
  ///
  /// @see TaskRunnerResults
  public interface Result {
    boolean isAborted();
    boolean hasErrors();
  }

  /// The implementation should provide execution of the specified [ProjectTask]s by either means.
  /// Only tasks which this [ProjectTaskRunner] [#canRun(Project, ProjectTask, ProjectTaskContext)] will be passed.
  /// It is expected that the [ProjectTaskRunner] will supply the returning [Promise]
  /// with the [Result] asynchronously when all tasks will be completed.
  ///
  /// @return promise of the execution result
  /// @see #canRun(Project, ProjectTask, ProjectTaskContext)
  public Promise<Result> run(@NotNull Project project, @NotNull ProjectTaskContext context, ProjectTask @NotNull ... tasks) {
    throw new UnsupportedOperationException();
  }

  /// @deprecated use [#canRun(Project, ProjectTask, ProjectTaskContext)] instead.
  @Deprecated(forRemoval = true)
  public boolean canRun(@SuppressWarnings("unused") @NotNull ProjectTask projectTask) {
    return false;
  }

  /// @deprecated use [#canRun(Project, ProjectTask, ProjectTaskContext)] instead.
  @Deprecated(forRemoval = true)
  public boolean canRun(@SuppressWarnings("unused") @NotNull Project project, @NotNull ProjectTask projectTask) {
    return canRun(projectTask);
  }

  /// Check if the task can be executed by the task runner.
  ///
  /// @param project     to which the task corresponds.
  /// @param projectTask to check.
  /// @param context     of the task.
  /// @return `true` if the task should be executed by this runner, `false` otherwise.
  public boolean canRun(@NotNull Project project, @NotNull ProjectTask projectTask, @Nullable ProjectTaskContext context) {
    return canRun(project, projectTask);
  }

  /// This method can be used when execution of some "Run Configuration" should be delegated to another tool.
  /// E.g., delegated run of an "ApplicationConfiguration" by external tool.
  public @Nullable ExecutionEnvironment createExecutionEnvironment(
    @NotNull Project project,
    @NotNull ExecuteRunConfigurationTask task,
    @Nullable Executor executor
  ) {
    return null;
  }

  @ApiStatus.Experimental
  public @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, ProjectTask @NotNull ... tasks) {
    if (tasks.length == 0) return null;
    if (tasks.length == 1 && tasks[0] instanceof ExecuteRunConfigurationTask ercTask) {
      return createExecutionEnvironment(project, ercTask, null);
    }
    return null;
  }

  /// The flag indicates if the [ProjectTaskRunner] supports reporting information about generated files during execution or not.
  /// The fine-grained events per generated files allow greatly improving IDE performance for some activities
  /// like fast hotswap reload after incremental compilation.
  ///
  /// The support means responsibility to send [ProjectTaskContext#fileGenerated] events per each generated file
  /// or at least supply effective output roots containing generated files using the [ProjectTaskContext#addDirtyOutputPathsProvider] method
  /// if per-file events are not possible.
  ///
  /// @return `true` if the [ProjectTaskRunner] supports reporting information about generated files during this runner tasks execution
  @ApiStatus.Experimental
  public boolean isFileGeneratedEventsSupported() {
    return false;
  }
}
