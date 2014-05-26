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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugUIEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaPatchableProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenericDebuggerRunner extends JavaPatchableProgramRunner<GenericDebuggerRunnerSettings> {
  @Override
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof ModuleRunProfile
           && !(profile instanceof RunConfigurationWithSuppressedDefaultDebugAction);
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return DebuggingRunnerData.DEBUGGER_RUNNER_ID;
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull final Project project,
                                           @NotNull final RunProfileState state,
                                           final RunContentDescriptor contentToReuse,
                                           @NotNull final ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    return createContentDescriptor(project, state, contentToReuse, env);
  }

  @Nullable
  protected RunContentDescriptor createContentDescriptor(Project project, RunProfileState state,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment env) throws ExecutionException {
    if (state instanceof JavaCommandLine) {
      final JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      runCustomPatchers(parameters, env.getExecutor(), env.getRunProfile());
      RemoteConnection connection = DebuggerManagerImpl.createDebugParameters(parameters, true, DebuggerSettings.getInstance().DEBUGGER_TRANSPORT, "", false);
      return attachVirtualMachine(project, state, contentToReuse, env, connection, true);
    }
    if (state instanceof PatchedRunnableState) {
      final RemoteConnection connection = doPatch(new JavaParameters(), env.getRunnerSettings());
      return attachVirtualMachine(project, state, contentToReuse, env, connection, true);
    }
    if (state instanceof RemoteState) {
      final RemoteConnection connection = createRemoteDebugConnection((RemoteState)state, env.getRunnerSettings());
      return attachVirtualMachine(project, state, contentToReuse, env, connection, false);
    }

    return null;
  }

  @Nullable
  protected RunContentDescriptor attachVirtualMachine(final Project project, RunProfileState state,
                                                      RunContentDescriptor contentToReuse,
                                                      ExecutionEnvironment env, RemoteConnection connection, boolean pollConnection)
    throws ExecutionException {
    DefaultDebugUIEnvironment debugEnvironment = new DefaultDebugUIEnvironment(project,
                                                                               env.getExecutor(),
                                                                               this,
                                                                               env,
                                                                               state,
                                                                               contentToReuse,
                                                                               connection,
                                                                               pollConnection);
    DebugEnvironment environment = debugEnvironment.getEnvironment();
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(environment);
    if (debuggerSession == null) {
      return null;
    }

    final DebugProcessImpl debugProcess = debuggerSession.getProcess();
    if (debugProcess.isDetached() || debugProcess.isDetaching()) {
      debuggerSession.dispose();
      return null;
    }
    if (environment.isRemote()) {
      // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
      // which is an expensive operation when executed first time
      debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
    }

    XDebugSession debugSession =
      XDebuggerManager.getInstance(project).startSession(this, env, contentToReuse, new XDebugProcessStarter() {
        @Override
        @NotNull
        public XDebugProcess start(@NotNull XDebugSession session) {
          XDebugSessionImpl sessionImpl = (XDebugSessionImpl)session;
          ExecutionResult executionResult = debugProcess.getExecutionResult();
          sessionImpl.addExtraActions(executionResult.getActions());
          if (executionResult instanceof DefaultExecutionResult) {
            sessionImpl.addRestartActions(((DefaultExecutionResult)executionResult).getRestartActions());
            sessionImpl.addExtraStopActions(((DefaultExecutionResult)executionResult).getAdditionalStopActions());
          }
          return new JavaDebugProcess(session, debuggerSession);
        }
      });
    return debugSession.getRunContentDescriptor();
  }

  private static RemoteConnection createRemoteDebugConnection(RemoteState connection, final RunnerSettings settings) {
    final RemoteConnection remoteConnection = connection.getRemoteConnection();

    GenericDebuggerRunnerSettings debuggerRunnerSettings = (GenericDebuggerRunnerSettings)settings;

    if (debuggerRunnerSettings != null) {
      remoteConnection.setUseSockets(debuggerRunnerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
      remoteConnection.setAddress(debuggerRunnerSettings.DEBUG_PORT);
    }

    return remoteConnection;
  }

  @Override
  public GenericDebuggerRunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return new GenericDebuggerRunnerSettings();
  }

  @Override
  public void patch(JavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, final boolean beforeExecution) throws ExecutionException {
    doPatch(javaParameters, settings);
    runCustomPatchers(javaParameters, Executor.EXECUTOR_EXTENSION_NAME.findExtension(DefaultDebugExecutor.class), runProfile);
  }

  private static RemoteConnection doPatch(final JavaParameters javaParameters, final RunnerSettings settings) throws ExecutionException {
    final GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings);
    if (StringUtil.isEmpty(debuggerSettings.getDebugPort())) {
      debuggerSettings.setDebugPort(DebuggerUtils.getInstance().findAvailableDebugAddress(debuggerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT));
    }
    return DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, false);
  }

  @Override
  public SettingsEditor<GenericDebuggerRunnerSettings> getSettingsEditor(final Executor executor, RunConfiguration configuration) {
    if (configuration instanceof RunConfigurationWithRunnerSettings) {
      if (((RunConfigurationWithRunnerSettings)configuration).isSettingsNeeded()) {
        return new GenericDebuggerParametersRunnerConfigurable(configuration.getProject());
      }
    }
    return null;
  }
}
