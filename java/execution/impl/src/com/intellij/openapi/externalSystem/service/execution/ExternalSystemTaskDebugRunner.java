/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.build.BuildView;
import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.RemoteConnectionStub;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.ServerSocket;

/**
 * @author Denis Zhdanov
 */
public class ExternalSystemTaskDebugRunner extends GenericDebuggerRunner {
  static final Logger LOG = Logger.getInstance(ExternalSystemTaskDebugRunner.class);

  private static final String ATTACH_VM_FAILED = "ATTACH_VM_FAILED";

  @NotNull
  @Override
  public String getRunnerId() {
    return ExternalSystemConstants.DEBUG_RUNNER_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return profile instanceof ExternalSystemRunConfiguration && DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    if (state instanceof ExternalSystemRunnableState) {
      ExternalSystemRunnableState runnableState = (ExternalSystemRunnableState)state;
      int port = runnableState.getDebugPort();
      if (port > 0) {
        RunContentDescriptor runContentDescriptor = doGetRunContentDescriptor(runnableState, environment);
        if (runContentDescriptor == null) return null;

        ProcessHandler processHandler = runContentDescriptor.getProcessHandler();
        final ServerSocket socket = runnableState.getForkSocket();
        if (socket != null && processHandler != null) {
          new ForkedDebuggerThread(processHandler, runContentDescriptor, socket, environment, runnableState).start();
        }
        return runContentDescriptor;
      }
      else {
        LOG.warn("Can't attach debugger to external system task execution. Reason: target debug port is unknown");
      }
    }
    else {
      LOG.warn(String.format(
        "Can't attach debugger to external system task execution. Reason: invalid run profile state is provided"
        + "- expected '%s' but got '%s'",
        ExternalSystemRunnableState.class.getName(), state.getClass().getName()
      ));
    }
    return null;
  }

  @Nullable
  private RunContentDescriptor doGetRunContentDescriptor(@NotNull ExternalSystemRunnableState state,
                                                         @NotNull ExecutionEnvironment environment) throws ExecutionException {
    RunContentDescriptor runContentDescriptor = createProcessToDebug(state, environment);
    if (runContentDescriptor == null) return null;

    state.setContentDescriptor(runContentDescriptor);

    ExecutionConsole executionConsole = runContentDescriptor.getExecutionConsole();
    if (executionConsole instanceof BuildView) {
      return runContentDescriptor;
    }
    RunContentDescriptor descriptor =
      new RunContentDescriptor(runContentDescriptor.getExecutionConsole(), runContentDescriptor.getProcessHandler(),
                               runContentDescriptor.getComponent(), runContentDescriptor.getDisplayName(),
                               runContentDescriptor.getIcon(), null,
                               runContentDescriptor.getRestartActions()) {
        @Override
        public boolean isHiddenContent() {
          return true;
        }
      };
    descriptor.setRunnerLayoutUi(runContentDescriptor.getRunnerLayoutUi());
    return descriptor;
  }

  @NotNull
  private XDebugProcess jvmProcessToDebug(@NotNull XDebugSession session,
                                          ExternalSystemRunnableState state,
                                          @NotNull ExecutionEnvironment env) throws ExecutionException {
    String debugPort = String.valueOf(state.getDebugPort());
    RemoteConnection connection = state.isDebugServerProcess()
                                  ? new RemoteConnection(true, "127.0.0.1", debugPort, true)
                                  : new RemoteConnectionStub(true, "127.0.0.1", debugPort, true);
    DebugEnvironment environment = new DefaultDebugEnvironment(env, state, connection, DebugEnvironment.LOCAL_START_TIMEOUT);

    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(env.getProject()).attachVirtualMachine(environment);

    if (debuggerSession == null) {
      throw new ExecutionException(ATTACH_VM_FAILED);
    }

    final DebugProcessImpl debugProcess = debuggerSession.getProcess();


    XDebugSessionImpl sessionImpl = (XDebugSessionImpl)session;
    ExecutionResult executionResult = debugProcess.getExecutionResult();
    sessionImpl.addExtraActions(executionResult.getActions());
    if (executionResult instanceof DefaultExecutionResult) {
      sessionImpl.addRestartActions(((DefaultExecutionResult)executionResult).getRestartActions());
    }
    return JavaDebugProcess.create(session, debuggerSession);
  }

  @Nullable
  private RunContentDescriptor createProcessToDebug(ExternalSystemRunnableState state,
                                                    @NotNull ExecutionEnvironment env) throws ExecutionException {

    RunContentDescriptor result;

    try {
      result = XDebuggerManager.getInstance(env.getProject()).startSession(env, new XDebugProcessStarter() {
        @Override
        @NotNull
        public XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException {
          XDebugProcess nonJvmDebugProcess = state.startDebugProcess(session, env);
          return nonJvmDebugProcess != null ? nonJvmDebugProcess : jvmProcessToDebug(session, state, env);
        }
      }).getRunContentDescriptor();
    }
    catch (ExecutionException e) {
      if (!e.getMessage().equals(ATTACH_VM_FAILED)) {
        throw e;
      }
      result = null;
    }

    return result;
  }
}
