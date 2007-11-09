package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;

public class TemplateProjectPropertiesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    ShowSettingsUtil.getInstance().showSettingsDialog(defaultProject, new ConfigurableGroup[]{
      new ProjectConfigurablesGroup(defaultProject)
    });
  }
}
