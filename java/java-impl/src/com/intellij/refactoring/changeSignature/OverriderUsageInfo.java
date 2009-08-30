package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;

/**
 * @author ven
 */
public class OverriderUsageInfo extends UsageInfo {
  private final PsiMethod myBaseMethod;
  private final boolean myToInsertArgs;
  private final boolean myToCatchExceptions;
  private final boolean myIsOriginalOverrider;

  public OverriderUsageInfo(final PsiMethod method, PsiMethod baseMethod, boolean  isOriginalOverrider,
                            boolean toInsertArgs, boolean toCatchExceptions) {
    super(method);
    myBaseMethod = baseMethod;
    myToInsertArgs = toInsertArgs;
    myToCatchExceptions = toCatchExceptions;
    myIsOriginalOverrider = isOriginalOverrider;
  }

  public PsiMethod getBaseMethod() {
    return myBaseMethod;
  }

  public PsiMethod getElement() {
    return (PsiMethod)super.getElement();
  }

  public boolean isOriginalOverrider() {
    return myIsOriginalOverrider;
  }

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToInsertArgs() {
    return myToInsertArgs;
  }
}
