package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ex.ProjectManagerEx;

public class TemplateProjectStructureAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    final Configurable configurable = ProjectConfigurablesGroup.getProjectStructureConfigurable(defaultProject);
    ShowSettingsUtil.getInstance().editConfigurable(defaultProject, OptionsEditorDialog.DIMENSION_KEY, configurable);
  }
}