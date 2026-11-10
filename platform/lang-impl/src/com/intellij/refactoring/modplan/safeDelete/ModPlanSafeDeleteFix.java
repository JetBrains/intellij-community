// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.model.SymbolPointer;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanCommandAction;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The safe delete of an element on the ModPlan engine, as a {@link com.intellij.modcommand.ModCommandAction}. It is not offered for an
 * element that the ModPlan safe delete does not support. See {@link ModPlanCommandAction} for the differences from the legacy flow.
 * <p>
 * The preview shows the text of the fix; a language may subclass it to show its own preview.
 */
@ApiStatus.Internal
public class ModPlanSafeDeleteFix extends ModPlanCommandAction {
  /**
   * @param optionValues the options of the deletion, such as {@link ModPlanSafeDeleteOptions#SEARCH_RELATED}
   * @param text         the text of the fix, such as "Safe delete 'foo'"
   */
  @RequiresReadLock
  public ModPlanSafeDeleteFix(@NotNull PsiElement element, @NotNull ModPlanOptionValues optionValues, @NotNull @IntentionName String text) {
    super(ModPlanSafeDeleteRefactoring.getInstance(),
          new ModPlanRequest(element.getProject(), List.of(SymbolPointer.of(element)), optionValues), element, text);
  }

  @Override
  public final @NotNull String getFamilyName() {
    return RefactoringBundle.message("safe.delete.family");
  }

  @Override
  public final boolean availableInBatchMode() {
    return false;
  }

  @Override
  public final @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = getAnchor();
    if (element == null || element.getContainingFile() != context.file()) return null;
    return super.getPresentation(context);
  }
}
