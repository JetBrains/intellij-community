// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author konstantin.aleev
 */
public class CopyConfigurationAction extends RunConfigurationTreeAction {
  public CopyConfigurationAction() {
    super(ExecutionBundle.message("copy.configuration.action.name"),
          ExecutionBundle.message("copy.configuration.action.name"),
          PlatformIcons.COPY_ICON);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setText(ExecutionBundle.message("copy.configuration.action.name") + "...");
    }
  }

  @Override
  protected boolean isEnabled4(RunDashboardRunConfigurationNode node) {
    Project project = node.getProject();
    return !project.isDisposed() && RunDashboardManager.getInstance(project).isShowConfigurations() &&
           RunManager.getInstance(node.getProject()).hasSettings(node.getConfigurationSettings());
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void doActionPerformed(RunDashboardRunConfigurationNode node) {
    RunManager runManager = RunManager.getInstance(node.getProject());
    RunnerAndConfigurationSettings settings = node.getConfigurationSettings();

    RunnerAndConfigurationSettings copiedSettings = ((RunnerAndConfigurationSettingsImpl)settings).clone();
    runManager.setUniqueNameIfNeed(copiedSettings);
    copiedSettings.setFolderName(settings.getFolderName());

    final ConfigurationFactory factory = settings.getFactory();
    if (factory instanceof ConfigurationFactoryEx) {
      ((ConfigurationFactoryEx)factory).onConfigurationCopied(settings.getConfiguration());
    }

    if (RunDialog.editConfiguration(node.getProject(), copiedSettings,
                                    ExecutionBundle.message("run.dashboard.edit.configuration.dialog.title"))) {
      copiedSettings.setShared(settings.isShared());
      runManager.addConfiguration(copiedSettings);
    }
  }
}
