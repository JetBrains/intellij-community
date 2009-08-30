package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceThisCallWithDelegateCall extends FixableUsageInfo {
    private final String delegateFieldName;
    private final PsiMethodCallExpression call;

     public ReplaceThisCallWithDelegateCall(PsiMethodCallExpression call, String delegateFieldName) {
        super(call);
        this.call = call;
        this.delegateFieldName = delegateFieldName;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression == null) {
            MutationUtils.replaceExpression(delegateFieldName + '.' + call.getText(), call);
        } else {
            MutationUtils.replaceExpression(delegateFieldName, qualifierExpression);
        }
    }
}
