/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 29, 2004
 * Time: 6:27:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class RemoteStateState implements RemoteState {
  private final Project    myProject;
  private final RemoteConnection myConnection;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationSettings;

  public RemoteStateState(Project project,
                          RemoteConnection connection,
                          RunnerSettings runnerSettings,
                          ConfigurationPerRunnerSettings configurationSettings) {
    myProject = project;
    myConnection = connection;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    ConsoleViewImpl consoleView = new ConsoleViewImpl(myProject, false);
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
    consoleView.attachToProcess(process);
    return new DefaultExecutionResult(consoleView, process, AnAction.EMPTY_ARRAY);
  }

  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }

}
