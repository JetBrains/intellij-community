// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RemoteStateState implements RemoteState {
  private final Project myProject;
  private final RemoteConnection myConnection;
  private final boolean myAutoRestart;

  public RemoteStateState(Project project, RemoteConnection connection) {
    this(project, connection, false);
  }

  public RemoteStateState(Project project, RemoteConnection connection, boolean autoRestart) {
    myProject = project;
    myConnection = connection;
    myAutoRestart = autoRestart;
  }

  @Override
  public ExecutionResult execute(final Executor executor, final @NotNull ProgramRunner<?> runner) throws ExecutionException {
    ConsoleViewImpl consoleView = new ConsoleViewImpl(myProject, false);
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject, myAutoRestart);
    consoleView.attachToProcess(process);
    return new DefaultExecutionResult(consoleView, process);
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }
}
