package com.intellij.debugger.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class GenericDebuggerRunner implements JavaProgramRunner<GenericDebuggerRunnerSettings> {
  private static final Icon ICON = IconLoader.getIcon("/actions/startDebugger.png");
  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/toolWindowDebugger.png");

  public static final Icon RERUN_ICON = ICON;
  private static final @NonNls String HELP_ID = "debugging";
  private static final RunnerInfo DEBUGGER_INFO = new RunnerInfo(ToolWindowId.DEBUG,
                                                                 DebuggerBundle.message("string.debugger.runner.description"),
                                                                 ICON,
                                                                 TOOLWINDOW_ICON,
                                                                 ToolWindowId.DEBUG,
                                                                 HELP_ID) {
    public String getRunContextActionId() {
      return "DebugClass";
    }

    public String getStartActionText() {
      return DebuggerBundle.message("debugger.runner.start.action.text");
    }
  };

  public GenericDebuggerRunner() {
  }

  public static RunnerInfo getRunnerInfo() {
    return DEBUGGER_INFO;
  }

  public @Nullable RunContentDescriptor doExecute(final RunProfileState state,
                                                                            final RunProfile runProfile,
                                                                            RunContentDescriptor reuseContent,
                                                                            final Project project) throws ExecutionException {
    final boolean addLvcsLabel = LvcsConfiguration.getInstance().ADD_LABEL_ON_RUNNING;
    final LocalVcs localVcs = LocalVcs.getInstance(project);
    RunContentDescriptor contentDescriptor = null;

    final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(project);
    if (state instanceof JavaCommandLine) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
      if (addLvcsLabel) {
        localVcs.addLabel(DebuggerBundle.message("debugger.runner.vcs.label.debugging", runProfile.getName()), "");
      }
      RemoteConnection connection = DebuggerManagerImpl.createDebugParameters(javaCommandLine.getJavaParameters(), true, DebuggerSettings.getInstance().DEBUGGER_TRANSPORT, "", false);
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, javaCommandLine, reuseContent, connection, true);
    }
    else if (state instanceof PatchedRunnableState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addLvcsLabel) {
        localVcs.addLabel(DebuggerBundle.message("debugger.runner.vcs.label.debugging", runProfile.getName()), "");
      }
      final RemoteConnection connection = doPatch(new JavaParameters(), state.getRunnerSettings());
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, state, reuseContent, connection, true);
    }
    else if (state instanceof RemoteState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addLvcsLabel) {
        localVcs.addLabel(DebuggerBundle.message("debugger.runner.vcs.label.remote.debug", runProfile.getName()), "");
      }
      RemoteState remoteState = (RemoteState)state;
      final RemoteConnection connection = createRemoteDebugConnection(remoteState, state.getRunnerSettings());
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, remoteState, reuseContent, connection, false);
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

  public RunnerInfo getInfo() {
    return DEBUGGER_INFO;
  }

  public GenericDebuggerRunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return new GenericDebuggerRunnerSettings();
  }

  public void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException {
    doPatch(javaParameters, settings);
  }

  public void checkConfiguration(final RunnerSettings settings, final ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException {
  }

  public void onProcessStarted(final RunnerSettings settings, final ExecutionResult executionResult) {
  }

  private static RemoteConnection doPatch(final JavaParameters javaParameters, final RunnerSettings settings) throws ExecutionException {
    final GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings.getData());
    return DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, false);
  }

  public AnAction[] createActions(ExecutionResult executionResult) {
    return new AnAction[0];
  }

  public SettingsEditor<GenericDebuggerRunnerSettings> getSettingsEditor(RunConfiguration configuration) {
    if (configuration instanceof RunConfigurationWithRunnerSettings) {
      if (((RunConfigurationWithRunnerSettings)configuration).isSettingsNeeded()) {
        return new GenericDebuggerParametersRunnerConfigurable(configuration.getProject());
      }
    }
    return null;
  }
}
