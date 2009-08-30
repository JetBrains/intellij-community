package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
    private final PsiReferenceExpression expression;
    private final String delegateClass;

    public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass) {
        super(expression);
        this.expression = expression;
        this.delegateClass = delegateClass;
    }

    public void fixUsage() throws IncorrectOperationException {
      MutationUtils.replaceExpression(delegateClass + '.' + expression.getReferenceName(), expression);
    }
}
