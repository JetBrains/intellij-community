/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
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

/**
 * @author Vladislav.Soroka
 * @since 2/18/14
 */
public class DefaultExternalSystemExecutionConsoleManager
  implements ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration, ExecutionConsole, ProcessHandler> {

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return ProjectSystemId.IDE;
  }

  @NotNull
  @Override
  public ExecutionConsole attachExecutionConsole(@NotNull ExternalSystemTask task,
                                                 @NotNull Project project,
                                                 @NotNull ExternalSystemRunConfiguration configuration,
                                                 @NotNull Executor executor,
                                                 @NotNull ExecutionEnvironment env,
                                                 @NotNull ProcessHandler processHandler) throws ExecutionException {
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
    return new AnAction[0];
  }
}
