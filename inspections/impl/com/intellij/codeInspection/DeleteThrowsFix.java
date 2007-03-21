package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class DeleteThrowsFix implements LocalQuickFix {
  private final MethodThrowsFix myQuickFix;

  public DeleteThrowsFix(PsiMethod method, PsiClassType exceptionClass) {
    myQuickFix = new MethodThrowsFix(method, exceptionClass, false, false);
  }

  @NotNull
  public String getName() {
    return myQuickFix.getText();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final PsiFile psiFile = element.getContainingFile();
    if (myQuickFix.isAvailable(project, null, psiFile)) {
      myQuickFix.invoke(project, null, psiFile);
    }
  }
}
