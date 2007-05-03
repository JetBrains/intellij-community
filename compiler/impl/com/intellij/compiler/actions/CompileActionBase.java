package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;

public abstract class CompileActionBase extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }

    doAction(dataContext, project);
  }

  protected abstract void doAction(final DataContext dataContext, final Project project);

  public void update(final AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(!CompilerManager.getInstance(project).isCompilationActive());
    }
  }
}
