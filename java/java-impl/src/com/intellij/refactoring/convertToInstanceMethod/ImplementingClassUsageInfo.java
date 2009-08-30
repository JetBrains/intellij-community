package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;

/**
 * @author dsl
 */
public class ImplementingClassUsageInfo extends UsageInfo {
  private final PsiClass myClass;
  public ImplementingClassUsageInfo(PsiClass aClass) {
    super(aClass);
    myClass = aClass;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }
}
