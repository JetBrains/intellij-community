package com.intellij.execution.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

public class EditConfigurationsDialog extends SingleConfigurableEditor {
  private RunConfigurable myConfigurable;

  public EditConfigurationsDialog(final Project project) {
    super(project, new RunConfigurable(project));
    myConfigurable = (RunConfigurable)getConfigurable();
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
  }

  protected void doOKAction() {
    super.doOKAction();
    RunManager.getInstance(getProject()).setActiveConfigurationFactory(myConfigurable.getSelectedConfigType());
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("project.propRunDebug");
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.execution.impl.EditConfigurationsDialog";
  }
}