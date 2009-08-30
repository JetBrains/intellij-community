package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAssignment extends FixableUsageInfo {
    private final PsiReferenceExpression reference;
    private final String originalClassName;

    public ReplaceStaticVariableAssignment(PsiReferenceExpression reference,
                                    String originalClassName) {
        super(reference);
        this.originalClassName = originalClassName;
        this.reference = reference;
    }

    public void fixUsage() throws IncorrectOperationException {
      MutationUtils.replaceExpression(originalClassName + '.' + reference.getText(), reference);
    }
}
