package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

public class ShowParameterInfoAction extends BaseCodeInsightAction{
  public ShowParameterInfoAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ShowParameterInfoHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return ShowParameterInfoHandler.getHandlers(PsiUtilBase.getLanguageAtOffset(file, editor.getCaretModel().getOffset())) != null;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}