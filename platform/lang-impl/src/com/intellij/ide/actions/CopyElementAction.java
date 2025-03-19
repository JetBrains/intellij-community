// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.copy.CopyHandler;
import org.jetbrains.annotations.NotNull;

public final class CopyElementAction extends AnAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement[] elements;

    PsiElement targetPsiElement = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext);
    PsiDirectory defaultTargetDirectory = targetPsiElement instanceof PsiDirectory ? (PsiDirectory)targetPsiElement : null;
    if (editor != null) {
      PsiElement aElement = getTargetElement(editor, project);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      elements = new PsiElement[]{aElement};
      if (aElement == null || !CopyHandler.canCopy(elements)) {
        elements = new PsiElement[]{file};
      }
    }
    else {
      elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    }
    doCopy(elements, defaultTargetDirectory);
  }

  private static void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    CopyHandler.doCopy(elements, defaultTargetDirectory);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(false);
    if (project == null) {
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      updateForEditor(dataContext, presentation);
    }
    else {
      updateForToolWindow(dataContext, presentation);
    }
  }

  private static void updateForEditor(DataContext dataContext, Presentation presentation) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setVisible(false);
      return;
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;

    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    PsiElement element = getTargetElement(editor, project);
    Ref<@NlsActions.ActionText String> actionName = new Ref<>();
    boolean result = element != null && CopyHandler.canCopy(new PsiElement[]{element}, actionName);

    if (!result && file != null) {
      result = CopyHandler.canCopy(new PsiElement[]{file}, actionName);
    }

    presentation.setEnabled(result);
    presentation.setVisible(true);
    if (!actionName.isNull()) {
      presentation.setText(actionName.get());
    }
  }

  private static void updateForToolWindow(DataContext dataContext, Presentation presentation) {
    PsiElement[] elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    Ref<@NlsActions.ActionText String> actionName = new Ref<>();
    presentation.setEnabled(elements != null && CopyHandler.canCopy(elements, actionName));
    if (!actionName.isNull()) {
      presentation.setText(actionName.get());
    }
  }

  private static PsiElement getTargetElement(final Editor editor, final Project project) {
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    PsiElement element = file.findElementAt(offset);
    if (element == null) element = file;
    return element;
  }
}
