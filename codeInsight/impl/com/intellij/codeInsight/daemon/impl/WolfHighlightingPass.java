package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.problems.WolfTheProblemSolverImpl;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.problems.WolfTheProblemSolver;

/**
 * @author cdr
*/
class WolfHighlightingPass extends ProgressableTextEditorHighlightingPass {
  public WolfHighlightingPass(final Project project, final Document document) {
    super(project, document, null, DaemonBundle.message("pass.wolf"));
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    final WolfTheProblemSolver solver = WolfTheProblemSolver.getInstance(myProject);
    if (solver instanceof WolfTheProblemSolverImpl) {
      ((WolfTheProblemSolverImpl)solver).startCheckingIfVincentSolvedProblemsYet(progress, this);
    }
  }

  protected void applyInformationWithProgress() {

  }
}
