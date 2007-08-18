package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction = null;

  public final void actionPerformed(AnActionEvent e) {
    LOG.assertTrue (!getClass ().equals (myInAction));
    try {
      myInAction = getClass();
      gotoActionPerformed (e);
    }
    catch (Throwable t) {
      myInAction = null;
    }
  }

  protected abstract void gotoActionPerformed(AnActionEvent e);

  public final void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(!getClass ().equals (myInAction) && project != null);
  }

  public static PsiElement getPsiContext(final AnActionEvent e) {
    PsiFile file = e.getData(DataKeys.PSI_FILE);
    if (file != null) return file;
    Project project = e.getData(DataKeys.PROJECT);
    return getPsiContext(project);
  }

  public static PsiElement getPsiContext(final Project project) {
    if (project == null) return null;
    Editor selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (selectedEditor == null) return null;
    Document document = selectedEditor.getDocument();
    return PsiDocumentManager.getInstance(project).getPsiFile(document);
  }
}
