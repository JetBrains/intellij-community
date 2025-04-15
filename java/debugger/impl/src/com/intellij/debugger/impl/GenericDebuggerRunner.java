// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.execution.impl.statistics.ProgramRunnerUsageCollector;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.execution.runners.JvmPatchableProgramRunner;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class GenericDebuggerRunner implements JvmPatchableProgramRunner<GenericDebuggerRunnerSettings> {
  private static final Logger LOG = Logger.getInstance(GenericDebuggerRunner.class);

  @Override
  public boolean canRun(final @NotNull String executorId, final @NotNull RunProfile profile) {
    return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof ModuleRunProfile
           && !(profile instanceof RunConfigurationWithSuppressedDefaultDebugAction);
  }

  @Override
  public @NotNull String getRunnerId() {
    return DebuggingRunnerData.DEBUGGER_RUNNER_ID;
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    RunProfileState state = environment.getState();
    if (state == null) {
      return;
    }

    Project project = environment.getProject();
    ExecutionManager executionManager = ExecutionManager.getInstance(project);
    RunProfile runProfile = environment.getRunProfile();
    StructuredIdeActivity activity = ProgramRunnerUsageCollector.INSTANCE.startExecute(project, this, runProfile);
    if (runProfile instanceof TargetEnvironmentAwareRunProfile &&
        state instanceof TargetEnvironmentAwareRunProfileState) {
      executionManager.startRunProfileWithPromise(environment, state, (ignored) -> {
        return doExecuteAsync((TargetEnvironmentAwareRunProfileState)state, environment).onSuccess((RunContentDescriptor descr) -> {
          ProgramRunnerUsageCollector.INSTANCE.finishExecute(activity, this, runProfile, true);
        });
      });
    }
    else {
      executionManager.startRunProfile(environment, state, state1 -> {
        return doExecute(state, environment);
      });
      ProgramRunnerUsageCollector.INSTANCE.finishExecute(activity, this, runProfile, false);
    }
  }

  // used externally
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    if (state instanceof JavaCommandLine &&
        !JavaProgramPatcher.patchJavaCommandLineParamsUnderProgress(env.getProject(), () -> {
          JavaProgramPatcher.runCustomPatchers(((JavaCommandLine)state).getJavaParameters(), env.getExecutor(), env.getRunProfile());
        })) {
      return null;
    }
    return createContentDescriptor(state, env);
  }

  protected @NotNull Promise<@Nullable RunContentDescriptor> doExecuteAsync(@NotNull TargetEnvironmentAwareRunProfileState state,
                                                                            @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    return state.prepareTargetToCommandExecution(env, LOG, "Failed to execute debug configuration async", () -> {
      if (state instanceof JavaCommandLine) {
        JavaProgramPatcher.runCustomPatchers(((JavaCommandLine)state).getJavaParameters(), env.getExecutor(), env.getRunProfile());
      }
      return createContentDescriptor(state, env);
    });
  }

  protected @Nullable RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state,
                                                                   @NotNull ExecutionEnvironment environment) throws ExecutionException {
    if (state instanceof JavaCommandLine) {
      JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      boolean isPollConnection = true;
      RemoteConnection connection = null;
      if (state instanceof RemoteConnectionCreator) {
        connection = ((RemoteConnectionCreator)state).createRemoteConnection(environment);
        isPollConnection = ((RemoteConnectionCreator)state).isPollConnection();
      }
      if (connection == null) {
        int transport = DebuggerSettings.getInstance().getTransport();
        connection = new RemoteConnectionBuilder(true, transport, transport == DebuggerSettings.SOCKET_TRANSPORT ? "0" : "")
          .asyncAgent(true)
          .project(environment.getProject())
          .create(parameters);
        isPollConnection = true;
      }

      return attachVirtualMachine(state, environment, connection, isPollConnection);
    }
    if (state instanceof PatchedRunnableState) {
      RemoteConnection connection =
        doPatch(new JavaParameters(), environment.getRunnerSettings(), true, environment.getProject());
      return attachVirtualMachine(state, environment, connection, true);
    }
    if (state instanceof RemoteState) {
      final RemoteConnection connection = createRemoteDebugConnection((RemoteState)state, environment.getRunnerSettings());
      return attachVirtualMachine(state, environment, connection, false);
    }

    return null;
  }

  protected @Nullable RunContentDescriptor attachVirtualMachine(RunProfileState state,
                                                                @NotNull ExecutionEnvironment env,
                                                                RemoteConnection connection,
                                                                boolean pollConnection) throws ExecutionException {
    return attachVirtualMachine(state, env, connection, pollConnection ? DebugEnvironment.LOCAL_START_TIMEOUT : 0);
  }


  protected @Nullable RunContentDescriptor attachVirtualMachine(RunProfileState state,
                                                                @NotNull ExecutionEnvironment env,
                                                                RemoteConnection connection,
                                                                long pollTimeout) throws ExecutionException {
    DebugEnvironment environment = new DefaultDebugEnvironment(env, state, connection, pollTimeout);
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(env.getProject()).attachVirtualMachine(environment);
    if (debuggerSession == null) {
      return null;
    }

    AtomicReference<ExecutionException> ex = new AtomicReference<>();
    AtomicReference<RunContentDescriptor> result = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        DebugProcessImpl debugProcess = debuggerSession.getProcess();
        result.set(XDebuggerManager.getInstance(env.getProject()).startSession(env, new XDebugProcessStarter() {
          @Override
          public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
            XDebugSessionImpl sessionImpl = (XDebugSessionImpl)session;
            ExecutionResult executionResult = debugProcess.getExecutionResult();
            sessionImpl.addExtraActions(executionResult.getActions());
            if (executionResult instanceof DefaultExecutionResult) {
              sessionImpl.addRestartActions(((DefaultExecutionResult)executionResult).getRestartActions());
            }
            sessionImpl.setPauseActionSupported(true); // enable pause by default
            return JavaDebugProcess.create(session, debuggerSession);
          }
        }).getRunContentDescriptor());
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (ExecutionException e) {
        ex.set(e);
      }
    }, ModalityState.any());
    if (ex.get() != null) throw ex.get();
    return result.get();
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
  public void patch(@NotNull JavaParameters javaParameters,
                    @Nullable RunnerSettings settings,
                    @NotNull RunProfile runProfile,
                    boolean beforeExecution) throws ExecutionException {
    doPatch(javaParameters, Objects.requireNonNull(settings), beforeExecution,
            runProfile instanceof RunConfiguration ? ((RunConfiguration)runProfile).getProject() : null);
    JavaProgramPatcher
      .runCustomPatchers(javaParameters, Executor.EXECUTOR_EXTENSION_NAME.findExtensionOrFail(DefaultDebugExecutor.class), runProfile);
  }

  private static RemoteConnection doPatch(@NotNull JavaParameters javaParameters,
                                          @NotNull RunnerSettings settings,
                                          boolean beforeExecution,
                                          @Nullable Project project)
    throws ExecutionException {
    GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings);
    if (StringUtil.isEmpty(debuggerSettings.getDebugPort())) {
      debuggerSettings.setDebugPort(
        DebuggerUtils.getInstance().findAvailableDebugAddress(debuggerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT));
    }
    return new RemoteConnectionBuilder(debuggerSettings.LOCAL, debuggerSettings.getTransport(), debuggerSettings.getDebugPort())
      .asyncAgent(beforeExecution)
      .project(project)
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
