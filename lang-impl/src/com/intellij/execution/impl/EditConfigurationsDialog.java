package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

public class EditConfigurationsDialog extends SingleConfigurableEditor {

  public EditConfigurationsDialog(final Project project) {
    super(project, new RunConfigurable(project));
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
  }

  protected void doOKAction() {
    super.doOKAction();    
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.rundebug");
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.execution.impl.EditConfigurationsDialog";
  }
}