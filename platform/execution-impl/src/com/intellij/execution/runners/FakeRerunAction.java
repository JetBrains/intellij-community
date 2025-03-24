// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
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

@ApiStatus.Internal
public class FakeRerunAction extends AnAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    RerunActionProxy actionProxy = getActionProxy(event);
    ExecutionEnvironmentProxy environment = getEnvironmentProxy(event);
    if (actionProxy == null || environment == null) {
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
    RunContentDescriptorProxy descriptor = getDescriptorProxy(event);
    boolean isRestart = ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()) || (descriptor != null && descriptor.isProcessRunning());
    presentation.setIcon(isRestart && !ExperimentalUI.isNewUI() ? AllIcons.Actions.Restart : rerunIcon);
    presentation.setEnabled(isEnabled(event));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    RerunActionProxy actionProxy = RerunActionProxy.EP_NAME.findFirstSafe(it -> it.isApplicable(event));
    ExecutionEnvironmentProxy environment = actionProxy != null ? actionProxy.getExecutionEnvironmentProxy(event) : null;
    if (environment != null) {
      environment.performRestart();
    }
  }

  protected @Nullable RunContentDescriptorProxy getDescriptorProxy(AnActionEvent event) {
    RerunActionProxy actionProxy = getActionProxy(event);
    if (actionProxy == null) {
      return null;
    }
    return actionProxy.getRunContentDescriptorProxy(event);
  }

  protected @Nullable ExecutionEnvironmentProxy getEnvironmentProxy(@NotNull AnActionEvent event) {
    RerunActionProxy actionProxy = getActionProxy(event);
    if (actionProxy == null) {
      return null;
    }
    return actionProxy.getExecutionEnvironmentProxy(event);
  }

  private static @Nullable RerunActionProxy getActionProxy(@NotNull AnActionEvent event) {
    return RerunActionProxy.EP_NAME.findFirstSafe(it -> it.isApplicable(event));
  }

  protected boolean isEnabled(@NotNull AnActionEvent event) {
    RunContentDescriptorProxy descriptor = getDescriptorProxy(event);
    ProcessHandlerProxy processHandler = descriptor == null ? null : descriptor.getProcessHandlerProxy();
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
