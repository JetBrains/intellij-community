package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeleteOverridingMethodUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {

  public SafeDeleteOverridingMethodUsageInfo(PsiMethod overridingMethod, PsiMethod method) {
    super(overridingMethod, method);
  }

  public PsiMethod getOverridingMethod() {
    return (PsiMethod) getElement();
  }

  public PsiMethod getReferencedMethod() {
    return (PsiMethod) getReferencedElement();
  }

  public void performRefactoring() throws IncorrectOperationException {
    getOverridingMethod().delete();
  }
}
