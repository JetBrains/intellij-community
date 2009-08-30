package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableIncrementDecrement extends FixableUsageInfo {
  private final PsiExpression reference;
  private final String originalClassName;

  public ReplaceStaticVariableIncrementDecrement(PsiExpression reference, String originalClassName) {
    super(reference);
    this.originalClassName = originalClassName;
    final PsiPrefixExpression prefixExpr = PsiTreeUtil.getParentOfType(reference, PsiPrefixExpression.class);
    if (prefixExpr != null) {
      this.reference = prefixExpr;
    }
    else {
      this.reference = PsiTreeUtil.getParentOfType(reference, PsiPostfixExpression.class);
    }
  }

  public void fixUsage() throws IncorrectOperationException {
    MutationUtils.replaceExpression(originalClassName + '.' + reference.getText(), reference);
  }
}
