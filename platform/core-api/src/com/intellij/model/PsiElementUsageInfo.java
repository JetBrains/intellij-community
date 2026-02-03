// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Simple PsiElement-based UsageInfo that supports branching
 */
public final class PsiElementUsageInfo extends UsageInfo {
  public PsiElementUsageInfo(@NotNull PsiElement element) {
    super(element);
  }
}
