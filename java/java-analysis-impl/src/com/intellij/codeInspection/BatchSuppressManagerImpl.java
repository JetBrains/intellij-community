// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BatchSuppressManagerImpl implements BatchSuppressManager {
  @Override
  public SuppressQuickFix @NotNull [] createBatchSuppressActions(@NotNull HighlightDisplayKey displayKey) {
    return new SuppressQuickFix[] {
        new SuppressByJavaCommentFix(displayKey),
        new SuppressLocalWithCommentFix(displayKey),
        new SuppressParameterFix(displayKey),
        new SuppressFix(displayKey),
        new SuppressForClassFix(displayKey),
        new SuppressAllForClassFix()
      };

  }

  @Override
  public boolean isSuppressedFor(final @NotNull PsiElement element, final @NotNull String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
  }

  @Override
  public @Nullable String getSuppressedInspectionIdsIn(@NotNull PsiElement element) {
    return JavaSuppressionUtil.getSuppressedInspectionIdsIn(element);
  }

  @Override
  public @Nullable PsiElement getElementToolSuppressedIn(final @NotNull PsiElement place, final @NotNull String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(place, toolId);
  }

  @Override
  public boolean canHave15Suppressions(final @NotNull PsiElement file) {
    return JavaSuppressionUtil.canHave15Suppressions(file);
  }

  @Override
  public boolean alreadyHas14Suppressions(final @NotNull PsiDocCommentOwner commentOwner) {
    return JavaSuppressionUtil.alreadyHas14Suppressions(commentOwner);
  }
}
