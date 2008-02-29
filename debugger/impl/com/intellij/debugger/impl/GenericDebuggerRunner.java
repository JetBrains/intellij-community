package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaPatchableProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenericDebuggerRunner extends JavaPatchableProgramRunner<GenericDebuggerRunnerSettings> {

  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof ModuleRunProfile;
  }

  @NotNull
  public String getRunnerId() {
    return "Debug";
  }

  @Nullable
  protected RunContentDescriptor doExecute(final Executor executor,
                                           final RunProfileState state,
                                           final RunProfile runProfile,
                                           final Project project,
                                           final RunContentDescriptor contentToReuse,
                                           final RunnerSettings settings,
                                           final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    final boolean addHistoryLabel = LocalHistoryConfiguration.getInstance().ADD_LABEL_ON_RUNNING;
    RunContentDescriptor contentDescriptor = null;

    final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(project);
    if (state instanceof JavaCommandLine) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
      if (addHistoryLabel) {
        LocalHistory.putSystemLabel(project, DebuggerBundle.message("debugger.runner.vcs.label.debugging", runProfile.getName()));
      }
      RemoteConnection connection = DebuggerManagerImpl
          .createDebugParameters(javaCommandLine.getJavaParameters(), true, DebuggerSettings.getInstance().DEBUGGER_TRANSPORT, "", false);
      contentDescriptor =
          manager.attachVirtualMachine(executor, (ModuleRunConfiguration)runProfile, this, javaCommandLine, contentToReuse, connection, true);
    }
    else if (state instanceof PatchedRunnableState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addHistoryLabel) {
        LocalHistory.putSystemLabel(project, DebuggerBundle.message("debugger.runner.vcs.label.debugging", runProfile.getName()));
      }
      final RemoteConnection connection = doPatch(new JavaParameters(), state.getRunnerSettings());
      contentDescriptor = manager.attachVirtualMachine(executor, (ModuleRunConfiguration)runProfile, this, state, contentToReuse, connection, true);
    }
    else if (state instanceof RemoteState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addHistoryLabel) {
        LocalHistory.putSystemLabel(project, DebuggerBundle.message("debugger.runner.vcs.label.remote.debug", runProfile.getName()));
      }
      RemoteState remoteState = (RemoteState)state;
      final RemoteConnection connection = createRemoteDebugConnection(remoteState, state.getRunnerSettings());
      contentDescriptor =
          manager.attachVirtualMachine(executor, (ModuleRunConfiguration)runProfile, this, remoteState, contentToReuse, connection, false);
    }

    return contentDescriptor != null ? contentDescriptor : null;
  }

  private static RemoteConnection createRemoteDebugConnection(RemoteState connection, final RunnerSettings settings) {
    final RemoteConnection remoteConnection = connection.getRemoteConnection();

    GenericDebuggerRunnerSettings debuggerRunnerSettings = ((GenericDebuggerRunnerSettings)settings.getData());

    if (debuggerRunnerSettings != null) {
      remoteConnection.setUseSockets(debuggerRunnerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
      remoteConnection.setAddress(debuggerRunnerSettings.DEBUG_PORT);
    }

    return remoteConnection;
  }

  public GenericDebuggerRunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return new GenericDebuggerRunnerSettings();
  }

  public void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException {
    doPatch(javaParameters, settings);
  }

  private static RemoteConnection doPatch(final JavaParameters javaParameters, final RunnerSettings settings) throws ExecutionException {
    final GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings.getData());
    return DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, false);
  }

  public SettingsEditor<GenericDebuggerRunnerSettings> getSettingsEditor(final Executor executor, RunConfiguration configuration) {
    if (configuration instanceof RunConfigurationWithRunnerSettings) {
      if (((RunConfigurationWithRunnerSettings)configuration).isSettingsNeeded()) {
        return new GenericDebuggerParametersRunnerConfigurable(configuration.getProject());
      }
    }
    return null;
  }
}
