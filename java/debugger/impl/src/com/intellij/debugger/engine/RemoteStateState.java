// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.application.JavaConsoleDecorator;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.DapMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteStateState implements RemoteState {
  private final Project myProject;
  private final RemoteConnection myConnection;
  private final boolean myAutoRestart;
  private final @Nullable RunConfigurationBase<?> myRunConfiguration;

  public RemoteStateState(Project project, RemoteConnection connection) {
    this(project, connection, false, null);
  }

  public RemoteStateState(Project project, RemoteConnection connection, boolean autoRestart) {
    this(project, connection, autoRestart, null);
  }

  public RemoteStateState(Project project, RemoteConnection connection, @NotNull RunConfigurationBase<?> runConfiguration) {
    this(project, connection, false, runConfiguration);
  }

  public RemoteStateState(Project project,
                          RemoteConnection connection,
                          @NotNull RunConfigurationBase<?> runConfiguration,
                          boolean autoRestart) {
    this(project, connection, autoRestart, runConfiguration);
  }

  private RemoteStateState(Project project,
                           RemoteConnection connection,
                           boolean autoRestart,
                           @Nullable RunConfigurationBase<?> runConfiguration) {
    myProject = project;
    myConnection = connection;
    myAutoRestart = autoRestart;
    myRunConfiguration = runConfiguration;
  }

  @Override
  public ExecutionResult execute(final Executor executor, final @NotNull ProgramRunner<?> runner) throws ExecutionException {
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject, myAutoRestart);
    if (DapMode.isDap()) {
      return new DefaultExecutionResult(null, process);
    }
    ConsoleView consoleView = new ConsoleViewImpl(myProject, false);
    if (myRunConfiguration != null) {
      consoleView = JavaConsoleDecorator.decorate(consoleView, myRunConfiguration, executor);
    }
    consoleView.attachToProcess(process);
    return new DefaultExecutionResult(consoleView, process);
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }
}
