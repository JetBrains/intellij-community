// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebugUIEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugUIEnvironment;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DebuggerPanelsManager {
  private final Project myProject;

  public DebuggerPanelsManager(Project project) {
    myProject = project;

    project.getMessageBus().connect().subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (executor == DefaultDebugExecutor.getDebugExecutorInstance()) {
          DebuggerSession session = descriptor == null ? null : getSession(project, descriptor);
          if (session != null) {
            getContextManager().setState(session.getContextManager().getContext(), session.getState(), DebuggerSession.Event.CONTEXT, null);
          }
          else {
            getContextManager().setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.State.DISPOSED, DebuggerSession.Event.CONTEXT, null);
          }
        }
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
      }
    });
  }

  private DebuggerStateManager getContextManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
  }

  @Nullable
  public RunContentDescriptor attachVirtualMachine(@NotNull ExecutionEnvironment environment,
                                                   RunProfileState state,
                                                   RemoteConnection remoteConnection,
                                                   boolean pollConnection) throws ExecutionException {
    return attachVirtualMachine(new DefaultDebugUIEnvironment(environment, state, remoteConnection, pollConnection));
  }

  @Nullable
  public RunContentDescriptor attachVirtualMachine(DebugUIEnvironment environment) throws ExecutionException {
    final DebugEnvironment modelEnvironment = environment.getEnvironment();
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(modelEnvironment);
    if (debuggerSession == null) {
      return null;
    }

    XDebugSession debugSession =
      XDebuggerManager.getInstance(myProject).startSessionAndShowTab(modelEnvironment.getSessionName(), environment.getReuseContent(), new XDebugProcessStarter() {
        @Override
        @NotNull
        public XDebugProcess start(@NotNull XDebugSession session) {
          return JavaDebugProcess.create(session, debuggerSession);
        }
      });
    return debugSession.getRunContentDescriptor();
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getComponent(DebuggerPanelsManager.class);
  }

  private static DebuggerSession getSession(Project project, RunContentDescriptor descriptor) {
    for (JavaDebugProcess process : XDebuggerManager.getInstance(project).getDebugProcesses(JavaDebugProcess.class)) {
      if (Comparing.equal(process.getProcessHandler(), descriptor.getProcessHandler())) {
        return process.getDebuggerSession();
      }
    }
    return null;
  }
}
