package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;

/**
 *  @author dsl
 */
public class NewParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;
  private final PsiMethod myMethod;

  public NewParameterCollidesWithLocalUsageInfo(PsiElement element, PsiElement referencedElement,
                                                PsiMethod method) {
    super(element, referencedElement);
    myConflictingElement = referencedElement;
    myMethod = method;
  }

  public String getDescription() {
    String buffer = RefactoringBundle.message("there.is.already.a.0.in.1.it.will.conflict.with.the.new.parameter",
                                     RefactoringUIUtil.getDescription(myConflictingElement, true),
                                     RefactoringUIUtil.getDescription(myMethod, true));

    return ConflictsUtil.capitalize(buffer);
  }
}
