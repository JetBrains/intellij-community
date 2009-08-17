package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

public class EditConfigurationsDialog extends SingleConfigurableEditor {

  public EditConfigurationsDialog(final Project project) {
    super(project, new RunConfigurable(project));
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.execution.impl.EditConfigurationsDialog";
  }
}