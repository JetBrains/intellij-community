package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class ShowSettingsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    ConfigurableGroup[] group = new ConfigurableGroup[]{
      new ProjectConfigurablesGroup(project),
      new IdeConfigurablesGroup()
    };

    ShowSettingsUtil.getInstance().showSettingsDialog(project, group);
  }
}
