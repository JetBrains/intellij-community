// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class SafeDeleteUsageInfo extends UsageInfo {
  private final PsiElement myReferencedElement;

  public SafeDeleteUsageInfo(@NotNull PsiElement element, PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
  }

  public SafeDeleteUsageInfo(PsiElement element, PsiElement referencedElement, int startOffset, int endOffset, boolean isNonCodeUsage) {
    super(element, startOffset, endOffset, isNonCodeUsage);
    myReferencedElement = referencedElement;
  }

  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }
}
