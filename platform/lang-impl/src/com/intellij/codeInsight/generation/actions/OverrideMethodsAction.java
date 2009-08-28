package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.OverrideMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

/**
 *
 */
public class OverrideMethodsAction extends BaseCodeInsightAction {

  protected CodeInsightActionHandler getHandler() {
    return new OverrideMethodsHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    Language language = PsiUtilBase.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.OVERRIDE_METHOD.forLanguage(language);
    if (codeInsightActionHandler != null) {
      return codeInsightActionHandler.isValidFor(editor, file);
    }
    return false;
  }

  public void update(final AnActionEvent event) {
    if (CodeInsightActions.OVERRIDE_METHOD.hasAnyExtensions()) {
      event.getPresentation().setVisible(true);
      super.update(event);
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }
}