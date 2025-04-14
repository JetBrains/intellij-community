// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.awt.*;

import static com.intellij.execution.runners.RunTab.EXECUTION_ENVIRONMENT_PROXY;

@ApiStatus.Internal
public class FakeRerunAction extends AnAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    ExecutionEnvironmentProxy environment = getEnvironmentProxy(event);
    if (environment == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    if (
      environment.isShowInDashboard() &&
      (ActionPlaces.RUNNER_TOOLBAR.equals(event.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(event.getPlace()))
    ) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setText(ExecutionBundle.messagePointer("rerun.configuration.action.name",
                                                        StringUtil.escapeMnemonics(environment.getRunProfileName())));
    Icon rerunIcon = ExperimentalUI.isNewUI() ? environment.getRerunIcon() : environment.getIcon();
    RunContentDescriptor descriptor = getDescriptor(event);
    boolean isRestart = ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()) ||
                        (descriptor != null && ExecutionManagerImpl.isProcessRunning(getDescriptor(event)));
    presentation.setIcon(isRestart && !ExperimentalUI.isNewUI() ? AllIcons.Actions.Restart : rerunIcon);
    presentation.setEnabled(isEnabled(event));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    ExecutionEnvironmentProxy environment = getEnvironmentProxy(event);
    if (environment != null) {
      environment.performRestart();
    }
  }

  protected @Nullable RunContentDescriptor getDescriptor(AnActionEvent event) {
    return event.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
  }

  protected @Nullable ExecutionEnvironmentProxy getEnvironmentProxy(@NotNull AnActionEvent event) {
    ExecutionEnvironmentProxy proxyFromContext = event.getData(EXECUTION_ENVIRONMENT_PROXY);
    if (proxyFromContext != null) {
      return proxyFromContext;
    }

    ExecutionEnvironment environmentFromContext = event.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT);
    if (environmentFromContext != null) {
      return new BackendExecutionEnvironmentProxy(environmentFromContext);
    }

    Project project = event.getProject();
    RunContentManager runContentManager = (project == null) ? null : RunContentManager.getInstanceIfCreated(project);
    if (runContentManager == null) {
      return null;
    }
    RunContentDescriptor contentDescriptor = runContentManager.getSelectedContent();
    if (contentDescriptor == null) {
      return null;
    }
    Component component = contentDescriptor.getComponent();
    if (component == null) {
      return null;
    }
    DataContext componentDataContext = DataManager.getInstance().getDataContext(component);
    ExecutionEnvironmentProxy proxyFromSelectedContent = EXECUTION_ENVIRONMENT_PROXY.getData(componentDataContext);
    if (proxyFromSelectedContent != null) {
      return proxyFromContext;
    }
    ExecutionEnvironment environmentFromSelectedContent = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(componentDataContext);
    if (environmentFromSelectedContent != null) {
      return new BackendExecutionEnvironmentProxy(environmentFromSelectedContent);
    }
    return null;
  }

  protected boolean isEnabled(@NotNull AnActionEvent event) {
    RunContentDescriptor descriptor = getDescriptor(event);
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    ExecutionEnvironmentProxy environment = getEnvironmentProxy(event);
    Project project = getEventProject(event);
    if (environment == null || project == null) {
      return false;
    }

    RunnerAndConfigurationSettingsProxy settings = environment.getRunnerAndConfigurationSettingsProxy();
    return (!DumbService.isDumb(project) || settings == null || settings.isDumbAware()) &&
           !environment.isStarting() &&
           !(processHandler != null && processHandler.isProcessTerminating());
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
