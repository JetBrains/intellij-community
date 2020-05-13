// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.execution.runners.JvmPatchableProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class GenericDebuggerRunner implements JvmPatchableProgramRunner<GenericDebuggerRunnerSettings> {
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
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    RunProfileState state = environment.getState();
    if (state == null) {
      return;
    }

    ExecutionManager executionManager = ExecutionManager.getInstance(environment.getProject());
    executionManager
      .executePreparationTasks(environment, state)
      .onSuccess(__ -> {
        executionManager.startRunProfile(environment, state, state1 -> {
          return doExecute(state, environment);
        });
      });
  }

  // used externally
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    return createContentDescriptor(state, env);
  }

  @Nullable
  protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    if (state instanceof JavaCommandLine) {
      JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      JavaProgramPatcher.runCustomPatchers(parameters, environment.getExecutor(), environment.getRunProfile());
      boolean isPollConnection = true;
      RemoteConnection connection = null;
      if (state instanceof RemoteConnectionCreator) {
        connection = ((RemoteConnectionCreator)state).createRemoteConnection(environment);
        isPollConnection = ((RemoteConnectionCreator)state).isPollConnection();
      }
      if (connection == null) {
        int transport = DebuggerSettings.getInstance().getTransport();
        connection = DebuggerManagerImpl.createDebugParameters(parameters,
                                                               true,
                                                               transport,
                                                               transport == DebuggerSettings.SOCKET_TRANSPORT ? "0" : "",
                                                               false);
        isPollConnection = true;
      }

      // TODO: remove in 2019.1 where setShouldKillProcessSoftlyWithWinP is enabled by default in KillableProcessHandler
      RunContentDescriptor descriptor = attachVirtualMachine(state, environment, connection, isPollConnection);
      if (descriptor != null) {
        ProcessHandler handler = descriptor.getProcessHandler();
        if (handler instanceof KillableProcessHandler) {
          ((KillableProcessHandler)handler).setShouldKillProcessSoftlyWithWinP(true);
        }
      }
      return descriptor;
    }
    if (state instanceof PatchedRunnableState) {
      final RemoteConnection connection = doPatch(new JavaParameters(), environment.getRunnerSettings(), true);
      return attachVirtualMachine(state, environment, connection, true);
    }
    if (state instanceof RemoteState) {
      final RemoteConnection connection = createRemoteDebugConnection((RemoteState)state, environment.getRunnerSettings());
      return attachVirtualMachine(state, environment, connection, false);
    }

    return null;
  }

  @Nullable
  protected RunContentDescriptor attachVirtualMachine(RunProfileState state,
                                                      @NotNull ExecutionEnvironment env,
                                                      RemoteConnection connection,
                                                      boolean pollConnection) throws ExecutionException {
    return attachVirtualMachine(state, env, connection, pollConnection ? DebugEnvironment.LOCAL_START_TIMEOUT : 0);
  }


  @Nullable
  protected RunContentDescriptor attachVirtualMachine(RunProfileState state,
                                                      @NotNull ExecutionEnvironment env,
                                                      RemoteConnection connection,
                                                      long pollTimeout) throws ExecutionException {
    DebugEnvironment environment = new DefaultDebugEnvironment(env, state, connection, pollTimeout);
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(env.getProject()).attachVirtualMachine(environment);
    if (debuggerSession == null) {
      return null;
    }

    final DebugProcessImpl debugProcess = debuggerSession.getProcess();
    return XDebuggerManager.getInstance(env.getProject()).startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull XDebugSession session) {
        XDebugSessionImpl sessionImpl = (XDebugSessionImpl)session;
        ExecutionResult executionResult = debugProcess.getExecutionResult();
        sessionImpl.addExtraActions(executionResult.getActions());
        if (executionResult instanceof DefaultExecutionResult) {
          sessionImpl.addRestartActions(((DefaultExecutionResult)executionResult).getRestartActions());
        }
        return JavaDebugProcess.create(session, debuggerSession);
      }
    }).getRunContentDescriptor();
  }

  private static RemoteConnection createRemoteDebugConnection(RemoteState connection, final RunnerSettings settings) {
    final RemoteConnection remoteConnection = connection.getRemoteConnection();

    GenericDebuggerRunnerSettings debuggerRunnerSettings = (GenericDebuggerRunnerSettings)settings;

    if (debuggerRunnerSettings != null) {
      remoteConnection.setUseSockets(debuggerRunnerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
      remoteConnection.setDebuggerAddress(debuggerRunnerSettings.getDebugPort());
    }

    return remoteConnection;
  }

  @Override
  public GenericDebuggerRunnerSettings createConfigurationData(@NotNull ConfigurationInfoProvider settingsProvider) {
    return new GenericDebuggerRunnerSettings();
  }

  // used externally
  @Override
  public void patch(@NotNull JavaParameters javaParameters, @Nullable RunnerSettings settings, @NotNull RunProfile runProfile, boolean beforeExecution) throws ExecutionException {
    doPatch(javaParameters, Objects.requireNonNull(settings), beforeExecution);
    JavaProgramPatcher.runCustomPatchers(javaParameters, Executor.EXECUTOR_EXTENSION_NAME.findExtensionOrFail(DefaultDebugExecutor.class), runProfile);
  }

  private static RemoteConnection doPatch(@NotNull JavaParameters javaParameters, @NotNull RunnerSettings settings, boolean beforeExecution)
    throws ExecutionException {
    GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings);
    if (StringUtil.isEmpty(debuggerSettings.getDebugPort())) {
      debuggerSettings.setDebugPort(DebuggerUtils.getInstance().findAvailableDebugAddress(debuggerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT));
    }
    return new RemoteConnectionBuilder(debuggerSettings.LOCAL, debuggerSettings.getTransport(), debuggerSettings.getDebugPort())
      .asyncAgent(beforeExecution)
      .memoryAgent(beforeExecution && DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT)
      .create(javaParameters);
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
