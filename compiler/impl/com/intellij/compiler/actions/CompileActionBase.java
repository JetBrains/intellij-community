package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.psi.PsiFile;

public abstract class CompileActionBase extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      return;
    }
    Editor editor = e.getData(DataKeys.EDITOR);
    PsiFile file = e.getData(DataKeys.PSI_FILE);
    if (file != null && editor != null) {
      DaemonCodeAnalyzerImpl.autoImportReferenceAtCursor(editor, file); //let autoimport complete
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
