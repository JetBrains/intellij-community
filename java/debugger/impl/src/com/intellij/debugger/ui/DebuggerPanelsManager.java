// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebugUIEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugUIEnvironment;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class DebuggerPanelsManager {
  private final Project myProject;

  public DebuggerPanelsManager(Project project) {
    myProject = project;
  }

  public @Nullable RunContentDescriptor attachVirtualMachine(@NotNull ExecutionEnvironment environment,
                                                             RunProfileState state,
                                                             RemoteConnection remoteConnection,
                                                             boolean pollConnection) throws ExecutionException {
    return attachVirtualMachine(new DefaultDebugUIEnvironment(environment, state, remoteConnection, pollConnection));
  }

  public @Nullable RunContentDescriptor attachVirtualMachine(DebugUIEnvironment environment) throws ExecutionException {
    final DebugEnvironment modelEnvironment = environment.getEnvironment();
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(modelEnvironment);
    if (debuggerSession == null) {
      return null;
    }

    XDebugSession debugSession =
      XDebuggerManager.getInstance(myProject).startSessionAndShowTab(modelEnvironment.getSessionName(), environment.getReuseContent(), new XDebugProcessStarter() {
        @Override
        public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
          return JavaDebugProcess.create(session, debuggerSession);
        }
      });
    return debugSession.getRunContentDescriptor();
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getService(DebuggerPanelsManager.class);
  }
}
