// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.actions.RunDashboardActionUtils;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author konstantin.aleev
 */
final class CopyConfigurationAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : RunDashboardActionUtils.getTarget(e);
    boolean enabled = node != null && RunManager.getInstance(project).hasSettings(node.getConfigurationSettings());
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    boolean popupPlace = e.isFromContextMenu();
    presentation.setVisible(enabled || !popupPlace);
    if (popupPlace) {
      presentation.setText(getTemplatePresentation().getText() + "...");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunDashboardRunConfigurationNode node = project == null ? null : RunDashboardActionUtils.getTarget(e);
    if (node == null) return;

    RunManager runManager = RunManager.getInstance(project);
    RunnerAndConfigurationSettings settings = node.getConfigurationSettings();

    RunnerAndConfigurationSettings copiedSettings = ((RunnerAndConfigurationSettingsImpl)settings).clone();
    runManager.setUniqueNameIfNeeded(copiedSettings);
    copiedSettings.setFolderName(settings.getFolderName());
    copiedSettings.getConfiguration().setBeforeRunTasks(settings.getConfiguration().getBeforeRunTasks());

    final ConfigurationFactory factory = settings.getFactory();
    RunConfiguration configuration = settings.getConfiguration();
    //noinspection deprecation
    if (factory instanceof ConfigurationFactoryEx) {
      //noinspection deprecation,unchecked
      ((ConfigurationFactoryEx)factory).onConfigurationCopied(configuration);
    }
    if (configuration instanceof RunConfigurationBase) {
      ((RunConfigurationBase<?>)configuration).onConfigurationCopied();
    }

    // fixme: action touches backend state and triggers client dialog - ask Lera?
    if (RunDialog.editConfiguration(project, copiedSettings,
                                    ExecutionBundle.message("run.dashboard.edit.configuration.dialog.title"))) {

      // todo rpc to backend
      runManager.addConfiguration(copiedSettings);
    }
  }
}
