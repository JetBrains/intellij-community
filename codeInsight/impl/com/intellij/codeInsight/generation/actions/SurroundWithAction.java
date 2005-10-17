package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class SurroundWithAction extends BaseCodeInsightAction{
  public SurroundWithAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler(){
    return new SurroundWithHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    if (file.canContainJavaCode()) return true;
    final Language language = file.getLanguage();
    return language.getSurroundDescriptors().length > 0;
  }
}