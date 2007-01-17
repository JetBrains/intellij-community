package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.problems.WolfTheProblemSolverImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.problems.WolfTheProblemSolver;

/**
 * @author cdr
*/
class WolfHighlightingPass extends ProgressableTextEditorHighlightingPass {
  public WolfHighlightingPass(final Project project) {
    super(project, null, null, "Wolf");
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
