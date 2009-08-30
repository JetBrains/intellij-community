package com.intellij.refactoring.util;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;


@SuppressWarnings({"AbstractClassExtendsConcreteClass"})
public abstract class FixableUsageInfo extends UsageInfo {
    public FixableUsageInfo(PsiElement element) {
        super(element);
    }

  public abstract void fixUsage() throws IncorrectOperationException;

  @Nullable
  public String getConflictMessage() {
    return null;
  }
}
