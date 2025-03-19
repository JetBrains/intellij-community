// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.util.NlsContexts;
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

  public @Nullable @NlsContexts.DialogMessage String getConflictMessage() {
    return null;
  }
}
