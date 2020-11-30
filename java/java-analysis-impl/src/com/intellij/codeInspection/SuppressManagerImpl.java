// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressManagerImpl extends SuppressManager implements RedundantSuppressionDetector {
  private static final Logger LOG = Logger.getInstance(SuppressManager.class);

  @Override
  public SuppressIntentionAction @NotNull [] createSuppressActions(@NotNull final HighlightDisplayKey displayKey) {
    SuppressQuickFix[] batchSuppressActions = createBatchSuppressActions(displayKey);
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions);
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    final HighlightDisplayKey displayKey = HighlightDisplayKey.findById(toolId);
    LOG.assertTrue(displayKey != null, "Display key is null for `" + toolId + "` tool");
    return createBatchSuppressActions(displayKey);
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
  public TextRange getHighlightingRange(PsiElement elementWithSuppression, String toolId) {
    PsiElement annotationOrTagElement = elementWithSuppression instanceof PsiComment ? null : getElementToolSuppressedIn(elementWithSuppression, toolId);
    if (annotationOrTagElement != null) {
      int shiftInParent = annotationOrTagElement.getTextRange().getStartOffset() - elementWithSuppression.getTextRange().getStartOffset();
      return RedundantSuppressionDetector.super.getHighlightingRange(annotationOrTagElement, toolId).shiftRight(shiftInParent);
    }
    return RedundantSuppressionDetector.super.getHighlightingRange(elementWithSuppression, toolId);
  }

  @Override
  public LocalQuickFix createRemoveRedundantSuppressionFix(@NotNull String toolId) {
    return new RemoveSuppressWarningAction(toolId);
  }
}
