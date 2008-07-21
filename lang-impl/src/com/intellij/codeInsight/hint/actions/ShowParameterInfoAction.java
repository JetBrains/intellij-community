package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.lang.Language;

public class ShowParameterInfoAction extends BaseCodeInsightAction{
  public ShowParameterInfoAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ShowParameterInfoHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final Language language = PsiUtilBase.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    return ShowParameterInfoHandler.getHandlers(language, file.getViewProvider().getBaseLanguage()) != null;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}