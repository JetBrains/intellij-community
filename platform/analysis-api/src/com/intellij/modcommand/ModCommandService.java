// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A support service for {@link ModCommand} and {@link ModCommandAction}. In general, should not be used directly.
 */
public interface ModCommandService {
  /**
   * @param action action
   * @return an {@link IntentionAction} wrapper that adapts this action to the old code which requires {@code IntentionAction}.
   * @see ModCommandAction#asIntention() 
   */
  @NotNull IntentionAction wrap(@NotNull ModCommandAction action);

  /**
   * @param action     action
   * @param psiElement context PsiElement
   * @return an {@link IntentionAction} wrapper that adapts this action to the old code which requires {@code LocalQuickFixAndIntentionActionOnPsiElement}.
   */
  @NotNull LocalQuickFixAndIntentionActionOnPsiElement wrapToLocalQuickFixAndIntentionActionOnPsiElement(@NotNull ModCommandAction action,
                                                                                                         @NotNull PsiElement psiElement);

  /**
   * Implementation of {@link LocalQuickFix#from(ModCommandAction)}. Should not be used directly
   */
  @NotNull LocalQuickFix wrapToQuickFix(@NotNull ModCommandAction action);

  /**
   * @param fix {@link LocalQuickFix}
   * @return a {@link ModCommandAction} which is wrapped inside the supplied quick-fix; null if the supplied quick-fix
   * does not wrap a {@code ModCommandAction}.
   */
  @Nullable ModCommandAction unwrap(@NotNull LocalQuickFix fix);

  /**
   * Implementation of ModCommand.psiUpdate; should not be used directly.
   */
  @NotNull ModCommand psiUpdate(@NotNull ActionContext context,
                                @NotNull Consumer<@NotNull ModPsiUpdater> updater);

  /**
   * Implementation of ModCommand.updateOption; should not be used directly 
   */
  <T extends InspectionProfileEntry> @NotNull ModCommand updateOption(
    @NotNull PsiElement context, @NotNull T inspection, @NotNull Consumer<@NotNull T> updater);

  /**
   * @return an instance of this service
   */
  static @NotNull ModCommandService getInstance() {
    return ApplicationManager.getApplication().getService(ModCommandService.class);
  }
}
