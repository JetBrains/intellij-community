package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiField;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemoveField extends FixableUsageInfo {
    private final PsiField field;

    public RemoveField(PsiField field) {
        super(field);
        this.field = field;
    }

    public void fixUsage() throws IncorrectOperationException {
        field.delete();
    }
}
