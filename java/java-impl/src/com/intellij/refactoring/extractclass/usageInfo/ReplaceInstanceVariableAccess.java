package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceInstanceVariableAccess extends FixableUsageInfo {
    private final PsiReferenceExpression expression;
    private final String getterName;
    private final String delegateName;

    public ReplaceInstanceVariableAccess(PsiReferenceExpression expression, String delegateName, String getterName) {
        super(expression);
        this.getterName = getterName;
        this.delegateName = delegateName;
        this.expression = expression;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiElement qualifier = expression.getQualifier();
        final String callString = delegateName + '.' + getterName + "()";
        if (qualifier != null) {
            final String qualifierText = qualifier.getText();
            MutationUtils.replaceExpression(qualifierText + '.' + callString, expression);
        } else {
            MutationUtils.replaceExpression(callString, expression);
        }
    }
}
