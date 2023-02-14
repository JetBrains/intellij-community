package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.PerformFixesModalTask;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class AbstractPerformFixesTask extends PerformFixesModalTask {
  private boolean myApplicableFixFound;
  protected final Class<?> myQuickfixClass;

  public AbstractPerformFixesTask(@NotNull Project project,
                                  CommonProblemDescriptor @NotNull [] descriptors,
                                  @Nullable Class<?> quickfixClass) {
    super(project, descriptors);
    myQuickfixClass = quickfixClass;
  }

  protected abstract <D extends CommonProblemDescriptor> void collectFix(QuickFix<D> fix, D descriptor, Project project);

  @Override
  protected final void applyFix(Project project, CommonProblemDescriptor descriptor) {
    //noinspection unchecked
    QuickFix<ProblemDescriptor>[] fixes = descriptor.getFixes();
    if (fixes != null) {
      for (final QuickFix<ProblemDescriptor> fix : fixes) {
        if (fix != null && (myQuickfixClass == null || fix.getClass().isAssignableFrom(myQuickfixClass))) {
          final ProblemDescriptor problemDescriptor = (ProblemDescriptor)descriptor;
          final PsiElement element = problemDescriptor.getPsiElement();
          if (element != null && element.isValid()) {
            collectFix(fix, problemDescriptor, project);
            myApplicableFixFound = true;
          }
          break;
        }
      }
    }
  }

  public final boolean isApplicableFixFound() {
    return myApplicableFixFound;
  }
}
