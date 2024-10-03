// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.ProjectRunConfigurationConfigurable;
import com.intellij.execution.ui.RunToolbarPopupKt;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EditRunConfigurationsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null && project.isDisposed()) {
      return;
    }
    if (project == null) {
      //setup template project configurations
      project = ProjectManager.getInstance().getDefaultProject();
    }
    RunnerAndConfigurationSettings configurationSettings = e.getData(RunToolbarPopupKt.RUN_CONFIGURATION_KEY);
    if (configurationSettings != null) {
      new EditConfigurationsDialog(project, new ProjectRunConfigurationConfigurable(project) {
        @Override
        protected RunnerAndConfigurationSettings getInitialSelectedConfiguration() {
          return configurationSettings;
        }
      }, e.getDataContext()).show();
    }
    else {
      new EditConfigurationsDialog(project, e.getDataContext()).show();
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    // we always show it enabled even in DumbMode,
    // 99% chances there will be some editable run configuration inside
    // and we don't want to check heavy conditions here
    presentation.setEnabled(true);

    if (e.getData(RunToolbarPopupKt.RUN_CONFIGURATION_KEY) != null) {
      presentation.setText(ExecutionBundle.message("choose.run.popup.edit"));
      presentation.setDescription(ExecutionBundle.message("choose.run.popup.edit.description"));
      if (!ExperimentalUI.isNewUI()) {
        presentation.setIcon(AllIcons.Actions.EditSource);
      }
    }
    else if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      presentation.setText(ExecutionBundle.messagePointer("edit.configuration.action"));
      presentation.setDescription(presentation.getText());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
