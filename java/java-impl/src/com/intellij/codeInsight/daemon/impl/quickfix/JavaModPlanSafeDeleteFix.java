// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/*
 * The {@link ModPlanSafeDeleteFix} of Java. Create it with {@link com.intellij.codeInsight.intention.QuickFixFactory#createSafeDeleteFix}.
 */
@ApiStatus.Internal
public final class JavaModPlanSafeDeleteFix extends ModPlanSafeDeleteFix {
  @RequiresReadLock
  public JavaModPlanSafeDeleteFix(@NotNull PsiElement element) {
    super(element, optionValuesOf(element), textOf(element));
  }

  /**
   * @return the options of the deletion of {@code element}; for a parameter, as the legacy safe delete fix deleted it: without the related
   * elements and without the search in comments and in non-code files
   */
  private static @NotNull ModPlanOptionValues optionValuesOf(@NotNull PsiElement element) {
    if (!(element instanceof PsiParameter)) return ModPlanOptionValues.EMPTY;
    return ModPlanOptionValues.EMPTY
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, false)
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, false)
      .with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false);
  }

  private static @NotNull @IntentionName String textOf(@NotNull PsiElement element) {
    String name = HighlightMessageUtil.getSymbolName(element, PsiSubstitutor.EMPTY,
                                                     PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT);
    return QuickFixBundle.message("safe.delete.text", ObjectUtils.notNull(name, ""));
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
