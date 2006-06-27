
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class GotoPreviousErrorAction extends BaseCodeInsightAction{
  public GotoPreviousErrorAction() {
    super(false);
  }

  protected CodeInsightActionHandler getHandler() {
    return new GotoNextErrorHandler(false);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
  }
}