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

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
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
  protected RunContentDescriptor doExecute(final Project project,
                                           final RunProfileState state,
                                           final RunContentDescriptor contentToReuse,
                                           final ExecutionEnvironment env) throws ExecutionException {
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
  protected RunContentDescriptor attachVirtualMachine(Project project, RunProfileState state,
                                                      RunContentDescriptor contentToReuse,
                                                      ExecutionEnvironment env, RemoteConnection connection, boolean pollConnection)
    throws ExecutionException {
    final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(project);
    return manager.attachVirtualMachine(env.getExecutor(), this, env, state, contentToReuse, connection, pollConnection);
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
