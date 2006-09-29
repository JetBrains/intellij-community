package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

public class EditRunConfigurationsAction extends AnAction{

  public EditRunConfigurationsAction() {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(IconLoader.getIcon("/actions/editSource.png"),0,2,2);
    icon.setIcon(new EmptyIcon(18), 1);
    getTemplatePresentation().setIcon(icon);
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null || project.isDisposed()) return;
    final EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }
                                                          
  @Nullable
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
