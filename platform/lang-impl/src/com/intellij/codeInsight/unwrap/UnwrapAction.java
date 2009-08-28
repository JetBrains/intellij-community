package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class UnwrapAction extends BaseCodeInsightAction{
  public UnwrapAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler(){
    return new UnwrapHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return !LanguageUnwrappers.INSTANCE.allForLanguage(file.getLanguage()).isEmpty();
  }
}