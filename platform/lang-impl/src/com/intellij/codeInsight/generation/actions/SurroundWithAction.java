package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageSurrounders;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

public class SurroundWithAction extends BaseCodeInsightAction{
  public SurroundWithAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler(){
    return new SurroundWithHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final Language language = file.getLanguage();
    if (!LanguageSurrounders.INSTANCE.allForLanguage(language).isEmpty()) {
      return true;
    }
    final PsiFile baseFile = PsiUtilBase.getTemplateLanguageFile(file);
    if (baseFile != null && baseFile != file && !LanguageSurrounders.INSTANCE.allForLanguage(baseFile.getLanguage()).isEmpty()) {
      return true;
    }

    if (!SurroundWithTemplateHandler.getApplicableTemplates(editor, file, true).isEmpty()) {
      return true;
    }

    return false;
  }
}