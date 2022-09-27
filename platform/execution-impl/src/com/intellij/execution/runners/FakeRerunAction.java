// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FakeRerunAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    ExecutionEnvironment environment = getEnvironment(event);
    if (environment != null) {
      presentation.setText(ExecutionBundle.messagePointer("rerun.configuration.action.name",
                                                          StringUtil.escapeMnemonics(environment.getRunProfile().getName())));
      Icon rerunIcon = ExperimentalUI.isNewUI() ? environment.getExecutor().getRerunIcon() : environment.getExecutor().getIcon();
      boolean isRestart = ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()) || ExecutionManagerImpl.isProcessRunning(getDescriptor(event));
      presentation.setIcon(isRestart && !ExperimentalUI.isNewUI() ? AllIcons.Actions.Restart : rerunIcon);
      presentation.setEnabled(isEnabled(event));
      return;
    }

    presentation.setEnabledAndVisible(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    ExecutionEnvironment environment = getEnvironment(event);
    if (environment != null) {
      MacroManager.getInstance().cacheMacrosPreview(event.getDataContext());
      ExecutionUtil.restart(environment);
    }
  }

  protected @Nullable RunContentDescriptor getDescriptor(AnActionEvent event) {
    return event.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
  }

  protected @Nullable ExecutionEnvironment getEnvironment(@NotNull AnActionEvent event) {
    ExecutionEnvironment environment = event.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT);
    if (environment == null) {
      Project project = event.getProject();
      RunContentManager runContentManager = project == null ? null : RunContentManager.getInstanceIfCreated(project);
      RunContentDescriptor contentDescriptor = runContentManager == null ? null : runContentManager.getSelectedContent();
      if (contentDescriptor != null) {
        JComponent component = contentDescriptor.getComponent();
        if (component != null) {
          environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
        }
      }
    }
    return environment;
  }

  protected boolean isEnabled(@NotNull AnActionEvent event) {
    RunContentDescriptor descriptor = getDescriptor(event);
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    ExecutionEnvironment environment = getEnvironment(event);
    Project project = getEventProject(event);
    if (environment == null || project == null) {
      return false;
    }

    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    return (!DumbService.isDumb(project) || settings == null || settings.getType().isDumbAware()) &&
           !ExecutionManager.getInstance(project).isStarting(environment) &&
           !(processHandler != null && processHandler.isProcessTerminating());
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
