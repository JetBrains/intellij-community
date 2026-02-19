// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class JavaInspectionSuppressor implements InspectionSuppressor, RedundantSuppressionDetector {

  private static final Logger LOG = Logger.getInstance(JavaInspectionSuppressor.class);

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    return JavaSuppressionUtil.getElementToolSuppressedIn(element, toolId) != null;
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    HighlightDisplayKey displayKey = HighlightDisplayKey.findById(toolId);
    LOG.assertTrue(displayKey != null, "Display key is null for `" + toolId + "` tool");
    return SuppressManager.getInstance().createBatchSuppressActions(displayKey);
  }

  @Override
  public String getSuppressionIds(@NotNull PsiElement element) {
    return JavaSuppressionUtil.getSuppressedInspectionIdsIn(element);
  }

  @Override
  public boolean isSuppressionFor(@NotNull PsiElement elementWithSuppression, @NotNull PsiElement place, @NotNull String toolId) {
    PsiElement suppressionScope = JavaSuppressionUtil.getElementToolSuppressedIn(place, toolId);
    return suppressionScope != null && PsiTreeUtil.isAncestor(elementWithSuppression, suppressionScope, false);
  }

  @Override
  public @Nullable TextRange getHighlightingRange(@NotNull PsiElement elementWithSuppression, @NotNull String toolId) {
    PsiElement annotationOrTagElement = elementWithSuppression instanceof PsiComment
                                        ? null : SuppressManager.getInstance().getElementToolSuppressedIn(elementWithSuppression, toolId);
    if (annotationOrTagElement != null) {
      int shiftInParent = annotationOrTagElement.getTextRange().getStartOffset() - elementWithSuppression.getTextRange().getStartOffset();
      if (shiftInParent < 0) {
        return getRangeFallback(elementWithSuppression, toolId); //non-normalized declaration
      }
      return Objects.requireNonNull(RedundantSuppressionDetector.super.getHighlightingRange(annotationOrTagElement, toolId))
        .shiftRight(shiftInParent);
    }
    return getRangeFallback(elementWithSuppression, toolId);
  }

  private @Nullable TextRange getRangeFallback(@NotNull PsiElement elementWithSuppression, @NotNull String toolId) {
    if (elementWithSuppression instanceof PsiNameIdentifierOwner owner) {
      PsiElement identifier = owner.getNameIdentifier();
      if (identifier != null) {
        return identifier.getTextRange().shiftLeft(elementWithSuppression.getTextRange().getStartOffset());
      }
    }
    return RedundantSuppressionDetector.super.getHighlightingRange(elementWithSuppression, toolId);
  }

  @Override
  public @NotNull LocalQuickFix createRemoveRedundantSuppressionFix(@NotNull String toolId) {
    return new RemoveSuppressWarningAction(toolId);
  }
}
