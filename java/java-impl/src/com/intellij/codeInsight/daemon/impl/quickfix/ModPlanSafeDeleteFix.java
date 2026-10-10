// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.model.SymbolPointer;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.modplan.ModPlanCommandAction;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The safe delete of an element on the ModPlan engine, as a {@link com.intellij.modcommand.ModCommandAction}. Create it with
 * {@link com.intellij.codeInsight.intention.QuickFixFactory#createSafeDeleteFix}. It is not offered for an element that the ModPlan safe delete does not support. See {@link ModPlanCommandAction} for the differences
 * from the legacy flow.
 */
@ApiStatus.Internal
public final class ModPlanSafeDeleteFix extends ModPlanCommandAction {
  @RequiresReadLock
  public ModPlanSafeDeleteFix(@NotNull PsiElement element) {
    super(ModPlanSafeDeleteRefactoring.getInstance(), requestOf(element), element, textOf(element));
  }

  /**
   * @return the deletion of {@code element}; for a parameter, as the legacy safe delete fix deleted it: without the related elements and
   * without the search in comments and in non-code files
   */
  @RequiresReadLock
  private static @NotNull ModPlanRequest requestOf(@NotNull PsiElement element) {
    if (!(element instanceof PsiParameter)) {
      return new ModPlanRequest(element.getProject(), List.of(SymbolPointer.of(element)), ModPlanOptionValues.EMPTY);
    }
    ModPlanOptionValues optionValues = ModPlanOptionValues.EMPTY
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, false)
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, false)
      .with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false);
    return new ModPlanRequest(element.getProject(), List.of(SymbolPointer.of(element)), optionValues);
  }

  private static @NotNull @IntentionName String textOf(@NotNull PsiElement element) {
    String name = HighlightMessageUtil.getSymbolName(element, PsiSubstitutor.EMPTY,
                                                     PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT);
    return QuickFixBundle.message("safe.delete.text", ObjectUtils.notNull(name, ""));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  @Override
  public boolean availableInBatchMode() {
    return false;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = getAnchor();
    // Should not be available for injected file, otherwise preview won't work
    if (element == null || element.getContainingFile() != context.file()) return null;
    return super.getPresentation(context);
  }

  /** The deletion of the element alone, as the usages are not searched for the preview. */
  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    PsiElement element = getAnchor();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    ModCommand deletion = ModCommand.psiUpdate(element, (copy, _) -> {
      if (copy.getParent() instanceof PsiClassOwner classOwner && classOwner.getClasses().length == 1 && classOwner.getClasses()[0] == copy) {
        classOwner.deleteChildRange(classOwner.getFirstChild(), classOwner.getLastChild());
      }
      else {
        copy.delete();
      }
    });
    return IntentionPreviewUtils.getModCommandPreview(deletion, context);
  }
}
