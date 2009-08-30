package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;

/**
 * @author ven
 */
public class InheritorUsageInfo extends UsageInfo {
  private final PsiClass myInheritor;

  public InheritorUsageInfo(final PsiClass inheritor) {
    super(inheritor);
    myInheritor = inheritor;
  }

  public PsiClass getInheritor() {
    return myInheritor;
  }
}
