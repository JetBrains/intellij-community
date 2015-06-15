/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the execution of run configurations and the relationship between running processes and Run/Debug toolwindow tabs.
 */
public abstract class ExecutionManager {
  public static final Topic<ExecutionListener> EXECUTION_TOPIC =
    Topic.create("configuration executed", ExecutionListener.class, Topic.BroadcastDirection.TO_PARENT);

  public static ExecutionManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExecutionManager.class);
  }

  /**
   * Returns the manager of running process tabs in Run and Debug toolwindows.
   *
   * @return the run content manager instance.
   */
  @NotNull
  public abstract RunContentManager getContentManager();

  /**
   * Executes the before launch tasks for a run configuration.
   *
   * @param startRunnable    the runnable to actually start the process for the run configuration.
   * @param environment              the execution environment describing the process to be started.
   * @param state            the ready-to-start process
   * @param onCancelRunnable the callback to call if one of the before launch tasks cancels the process execution.
   */
  public abstract void compileAndRun(@NotNull Runnable startRunnable,
                                     @NotNull ExecutionEnvironment environment,
                                     @Nullable RunProfileState state,
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
   * @param state   the ready-to-start process
   * @param environment     the execution environment describing the process to be started.
   */
  public abstract void startRunProfile(@NotNull RunProfileStarter starter,
                                       @NotNull RunProfileState state,
                                       @NotNull ExecutionEnvironment environment);

  public abstract void restartRunProfile(@NotNull Project project,
                                         @NotNull Executor executor,
                                         @NotNull ExecutionTarget target,
                                         @Nullable RunnerAndConfigurationSettings configuration,
                                         @Nullable ProcessHandler processHandler);

  public abstract void restartRunProfile(@NotNull ExecutionEnvironment environment);
}
