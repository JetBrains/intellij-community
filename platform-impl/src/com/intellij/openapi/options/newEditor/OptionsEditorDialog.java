package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class OptionsEditorDialog extends DialogWrapper {

  private Project myProject;
  private ConfigurableGroup[] myGroups;
  private Configurable myPreselected;

  public OptionsEditorDialog(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    super(project, true);
    myProject = project;
    myGroups = groups;
    myPreselected = preselectedConfigurable;

    init();
  }

  protected JComponent createCenterPanel() {
    return new OptionsEditor(myProject, myGroups, myPreselected);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "OptionsEditor";
  }
}