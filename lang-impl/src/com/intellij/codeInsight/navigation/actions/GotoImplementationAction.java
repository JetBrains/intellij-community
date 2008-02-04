package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.searches.DefinitionsSearch;

public class GotoImplementationAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler(){
    return new GotoImplementationHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  public void update(final AnActionEvent event) {
    if (!DefinitionsSearch.INSTANCE.hasAnyExecutors()) {
      event.getPresentation().setVisible(false);
    }
    else {
      super.update(event);
    }
  }
}