package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;

public class CompileDirtyAction extends CompileActionBase {

  protected void doAction(DataContext dataContext, Project project) {
    CompilerManager.getInstance(project).make(null);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    presentation.setEnabled(event.getDataContext().getData(DataConstants.PROJECT) != null);
  }
}