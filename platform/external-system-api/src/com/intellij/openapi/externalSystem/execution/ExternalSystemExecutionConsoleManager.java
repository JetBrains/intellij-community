// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.execution;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface ExternalSystemExecutionConsoleManager<
  ExternalSystemExecutionConsole extends ExecutionConsole,
  ExternalSystemProcessHandler extends ProcessHandler> {
  ExtensionPointName<ExternalSystemExecutionConsoleManager> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemExecutionConsoleManager");

  @NotNull
  ProjectSystemId getExternalSystemId();

  @Nullable
  default ExternalSystemExecutionConsole attachExecutionConsole(@NotNull Project project,
                                                                @NotNull ExternalSystemTask task,
                                                                @Nullable ExecutionEnvironment env,
                                                                @Nullable ExternalSystemProcessHandler processHandler) {return null;}

  void onOutput(@NotNull ExternalSystemExecutionConsole executionConsole,
                @NotNull ExternalSystemProcessHandler processHandler,
                @NotNull String text,
                @NotNull Key processOutputType);

  default void onStatusChange(@NotNull ExternalSystemExecutionConsole executionConsole,
                              @NotNull ExternalSystemTaskNotificationEvent event) { }

  boolean isApplicableFor(@NotNull ExternalSystemTask task);

  AnAction[] getRestartActions(@NotNull ExternalSystemExecutionConsole consoleView);

  @ApiStatus.Experimental
  default Filter[] getCustomExecutionFilters(@NotNull Project project,
                                             @NotNull ExternalSystemTask task,
                                             @Nullable ExecutionEnvironment env) { return Filter.EMPTY_ARRAY; }

  @ApiStatus.Experimental
  default AnAction[] getCustomActions(@NotNull Project project,
                                      @NotNull ExternalSystemTask task,
                                      @Nullable ExecutionEnvironment env) { return AnAction.EMPTY_ARRAY; }

  @ApiStatus.Experimental
  default AnAction[] getCustomContextActions(@NotNull Project project,
                                             @NotNull ExternalSystemTask task,
                                             @Nullable ExecutionEnvironment env) { return AnAction.EMPTY_ARRAY; }

}
