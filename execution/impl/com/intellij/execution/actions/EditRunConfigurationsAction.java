package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;

public class EditRunConfigurationsAction extends AnAction{
  public EditRunConfigurationsAction() {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(IconLoader.getIcon("/actions/editSource.png"),0,2,2);
    icon.setIcon(new EmptyIcon(18), 1);
    getTemplatePresentation().setIcon(icon);
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null || project.isDisposed()) return;
    final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  public void update(final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(e.getData(DataKeys.PROJECT) != null);
    if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      presentation.setText(ExecutionBundle.message("edit.configuration.action"));
      presentation.setDescription(presentation.getText());
    }
  }
}
