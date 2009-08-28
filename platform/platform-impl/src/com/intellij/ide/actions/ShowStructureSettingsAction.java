package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.diagnostic.Logger;

public class ShowStructureSettingsAction extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowStructureSettingsAction");

  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    final Configurable configurable = ProjectConfigurablesGroup.getProjectStructureConfigurable(project);
    if (configurable != null) {
      ShowSettingsUtil.getInstance().editConfigurable(project, OptionsEditorDialog.DIMENSION_KEY, configurable);
    } else {
      LOG.info("No project structure configurable found");
    }
  }
}