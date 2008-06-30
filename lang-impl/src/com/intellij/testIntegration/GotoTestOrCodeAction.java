package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class GotoTestOrCodeAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler(){
    return new GotoTestOrCodeHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }
}
