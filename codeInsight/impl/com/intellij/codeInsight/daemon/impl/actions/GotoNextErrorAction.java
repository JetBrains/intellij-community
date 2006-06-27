
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.psi.*;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

public class GotoNextErrorAction extends BaseCodeInsightAction{

  public GotoNextErrorAction() {
    super(false);
  }

  protected CodeInsightActionHandler getHandler() {
    return new GotoNextErrorHandler(true);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
  }
}