// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class DefaultExternalSystemExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> {

  @Override
  public @NotNull ProjectSystemId getExternalSystemId() {
    return ProjectSystemId.IDE;
  }

  @Override
  public @Nullable ExecutionConsole attachExecutionConsole(@NotNull Project project,
                                                           @NotNull ExternalSystemTask task,
                                                           @Nullable ExecutionEnvironment env,
                                                           @Nullable ProcessHandler processHandler) {
    ConsoleView executionConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    executionConsole.attachToProcess(processHandler);
    return executionConsole;
  }

  @Override
  public void onOutput(@NotNull ExecutionConsole executionConsole,
                       @NotNull ProcessHandler processHandler,
                       @NotNull String text,
                       @NotNull Key processOutputType) {
    processHandler.notifyTextAvailable(text, processOutputType);
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    return true;
  }

  @Override
  public AnAction[] getRestartActions(@NotNull ExecutionConsole consoleView) {
    return AnAction.EMPTY_ARRAY;
  }
}
