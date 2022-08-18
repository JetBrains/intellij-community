// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

public class EditRunConfigurationsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null && project.isDisposed()) {
      return;
    }
    if (project == null) {
      //setup template project configurations
      project = ProjectManager.getInstance().getDefaultProject();
    }
    new EditConfigurationsDialog(project).show();
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    // we always show it enabled even in DumbMode,
    // 99% chances there will be some editable run configuration inside
    // and we don't want to check heavy conditions here
    presentation.setEnabled(true);

    if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      presentation.setText(ExecutionBundle.messagePointer("edit.configuration.action"));
      presentation.setDescription(presentation.getText());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
