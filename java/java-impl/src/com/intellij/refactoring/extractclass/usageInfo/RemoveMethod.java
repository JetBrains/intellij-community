package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemoveMethod extends FixableUsageInfo {
    private final PsiMethod method;

    public RemoveMethod(PsiMethod method) {
        super(method);
        this.method = method;
    }

    public void fixUsage() throws IncorrectOperationException {
        method.delete();
    }
}
