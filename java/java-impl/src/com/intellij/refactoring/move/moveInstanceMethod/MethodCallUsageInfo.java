package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.usageView.UsageInfo;

/**
 * @author ven
 */
public class MethodCallUsageInfo extends UsageInfo {
  private final PsiMethodCallExpression myMethodCallExpression;
  private final boolean myIsInternal;

  public MethodCallUsageInfo(final PsiReferenceExpression referenceExpression, final boolean internal) {
    super(referenceExpression);
    myIsInternal = internal;
    myMethodCallExpression = (PsiMethodCallExpression)referenceExpression.getParent();
  }

  public PsiMethodCallExpression getMethodCallExpression() {
    return myMethodCallExpression;
  }

  public boolean isInternal() {
    return myIsInternal;
  }
}
