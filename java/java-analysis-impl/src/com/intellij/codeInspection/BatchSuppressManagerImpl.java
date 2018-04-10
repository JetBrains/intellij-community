// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BatchSuppressManagerImpl implements BatchSuppressManager {
  @NotNull
  @Override
  public SuppressQuickFix[] createBatchSuppressActions(@NotNull HighlightDisplayKey displayKey) {
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
  public boolean isSuppressedFor(@NotNull final PsiElement element, @NotNull final String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
  }

  @Override
  @Nullable
  public String getSuppressedInspectionIdsIn(@NotNull PsiElement element) {
    return JavaSuppressionUtil.getSuppressedInspectionIdsIn(element);
  }

  @Override
  @Nullable
  public PsiElement getElementToolSuppressedIn(@NotNull final PsiElement place, @NotNull final String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(place, toolId);
  }

  @Override
  public boolean canHave15Suppressions(@NotNull final PsiElement file) {
    return JavaSuppressionUtil.canHave15Suppressions(file);
  }

  @Override
  public boolean alreadyHas14Suppressions(@NotNull final PsiDocCommentOwner commentOwner) {
    return JavaSuppressionUtil.alreadyHas14Suppressions(commentOwner);
  }
}
