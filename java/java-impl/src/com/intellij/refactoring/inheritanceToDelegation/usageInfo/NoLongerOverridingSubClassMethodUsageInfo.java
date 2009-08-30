package com.intellij.refactoring.inheritanceToDelegation.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class NoLongerOverridingSubClassMethodUsageInfo extends UsageInfo {
  private final PsiMethod myOverridenMethod;

  public NoLongerOverridingSubClassMethodUsageInfo(PsiMethod subClassMethod, PsiMethod overridenMethod) {
    super(subClassMethod);
    myOverridenMethod = overridenMethod;
  }

  public PsiMethod getSubClassMethod() {
    return (PsiMethod) getElement();
  }

  public PsiMethod getOverridenMethod() {
    return myOverridenMethod;
  }

}
