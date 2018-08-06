// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author lex
 */
public class RemoteStateState implements RemoteState {
  private final Project    myProject;
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

  public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    ConsoleViewImpl consoleView = new ConsoleViewImpl(myProject, false);
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject, myAutoRestart);
    consoleView.attachToProcess(process);
    return new DefaultExecutionResult(consoleView, process);
  }

  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }

}
