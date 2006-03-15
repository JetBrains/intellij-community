package com.intellij.codeInspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;

/**
 * @author cdr
 */
public class DeleteThrowsFix implements LocalQuickFix {
  private final MethodThrowsFix myQuickFix;

  public DeleteThrowsFix(PsiMethod method, PsiClassType exceptionClass) {
    myQuickFix = new MethodThrowsFix(method, exceptionClass, false, false);
  }

  public String getName() {
    return myQuickFix.getText();
  }

  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
    if (myQuickFix.isAvailable(project, null, psiFile)) {
      myQuickFix.invoke(project, null, psiFile);
    }
  }
}
