package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.idea.ActionsBundle;

public class GotoTestOrCodeAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler(){
    return new GotoTestOrCodeHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public void update(AnActionEvent event) {
    Project project = event.getData(PlatformDataKeys.PROJECT);
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
    if (editor == null || project == null) return;

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    PsiElement element = GotoTestOrCodeHandler.getSelectedElement(editor, psiFile);
    Presentation p = event.getPresentation();

    if (element == null) {
      p.setEnabled(false);
    }
    else {
      p.setEnabled(true);
      if (TestFinderHelper.isTest(element)) {
        p.setText(ActionsBundle.message("action.GotoTestSubject.text"));
        p.setDescription(ActionsBundle.message("action.GotoTestSubject.description"));
      } else {
        p.setText(ActionsBundle.message("action.GotoTest.text"));
        p.setDescription(ActionsBundle.message("action.GotoTest.description"));
      }
    }
  }
}
