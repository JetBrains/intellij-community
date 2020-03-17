// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the execution of run configurations and the relationship between running processes and Run/Debug toolwindow tabs.
 */
public abstract class ExecutionManager {
  public static final Topic<ExecutionListener> EXECUTION_TOPIC =
    Topic.create("configuration executed", ExecutionListener.class, Topic.BroadcastDirection.TO_PARENT);

  public static ExecutionManager getInstance(@NotNull Project project) {
    return project.getService(ExecutionManager.class);
  }

  /**
   * @deprecated Use {@link RunContentManager#getInstance(Project)}
   */
  @NotNull
  @Deprecated
  public abstract RunContentManager getContentManager();

  /**
   * Executes the before launch tasks for a run configuration.
   *
   * @param startRunnable    the runnable to actually start the process for the run configuration.
   * @param environment              the execution environment describing the process to be started.
   * @param onCancelRunnable the callback to call if one of the before launch tasks cancels the process execution.
   */
  public abstract void compileAndRun(@NotNull Runnable startRunnable,
                                     @NotNull ExecutionEnvironment environment,
                                     @Nullable Runnable onCancelRunnable);

  /**
   * Returns the list of processes managed by all open run and debug tabs.
   *
   * @return the list of processes.
   */
  @NotNull
  public abstract ProcessHandler[] getRunningProcesses();

  /**
   * Prepares the run or debug tab for running the specified process and calls a callback to start it.
   *
   * @param starter the callback to start the process execution.
   * @param environment     the execution environment describing the process to be started.
   */
  public abstract void startRunProfile(@NotNull RunProfileStarter starter, @NotNull ExecutionEnvironment environment);

  /**
   * @deprecated Use {@link #startRunProfile(RunProfileStarter, ExecutionEnvironment)}
   */
  @Deprecated
  public void startRunProfile(@NotNull RunProfileStarter starter, @SuppressWarnings("unused") @NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) {
    startRunProfile(starter, environment);
  }

  public abstract void restartRunProfile(@NotNull Project project,
                                         @NotNull Executor executor,
                                         @NotNull ExecutionTarget target,
                                         @Nullable RunnerAndConfigurationSettings configuration,
                                         @Nullable ProcessHandler processHandler);

  public abstract void restartRunProfile(@NotNull ExecutionEnvironment environment);

  public final boolean isStarting(@NotNull ExecutionEnvironment environment) {
    return isStarting(environment.getExecutor().getId(), environment.getRunner().getRunnerId());
  }

  @ApiStatus.Internal
  public abstract boolean isStarting(@NotNull String executorId, @NotNull String runnerId);
}
