package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

/**
 *
 */
public class GotoSuperAction extends BaseCodeInsightAction implements CodeInsightActionHandler {

  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    final Language language = PsiUtilBase.getLanguageAtOffset(file, offset);

    final CodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.GOTO_SUPER.forLanguage(language);
    if (codeInsightActionHandler != null) {
      codeInsightActionHandler.invoke(project, editor, file);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void update(final AnActionEvent event) {
    if (CodeInsightActions.GOTO_SUPER.hasAnyExtensions()) {
      event.getPresentation().setVisible(true);
      super.update(event);
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }
}
