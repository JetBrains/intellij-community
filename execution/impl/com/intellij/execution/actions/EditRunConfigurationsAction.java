package com.intellij.execution.actions;

import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

public class EditRunConfigurationsAction extends AnAction{

  public void actionPerformed(final AnActionEvent e) {
    final Project project = getProject(e);
    final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  private static Project getProject(final AnActionEvent e) {
    return (Project)e.getDataContext().getData(DataConstants.PROJECT);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getProject(e) != null);
    if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      e.getPresentation().setText(ExecutionBundle.message("edit.configuration.action"));
    }
  }
}
