// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A support service for {@link ModCommand} and {@link ModCommandAction}. In general, it should not be used directly.
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
   * Chooses between host and injected file (if applicable) and performs the {@link ModCommandAction} in background thread,
   * returning the final command to execute, along with the selected context. May display UI to cancel the command 
   * if it takes too long time. 
   * 
   * @param hostFile host file
   * @param hostEditor host editor
   * @param commandAction command action to perform
   * @param fixOffset an offset in the host file to pass to the action, or -1 if editor caret offset should be used 
   * @return a command with context; null if the action was canceled. The command is not executed yet. 
   */
  @RequiresEdt
  @Nullable ModCommandWithContext chooseFileAndPerform(@NotNull PsiFile hostFile,
                                                       @Nullable Editor hostEditor,
                                                       @NotNull ModCommandAction commandAction,
                                                       int fixOffset);

  /**
   * @return an instance of this service
   */
  static @NotNull ModCommandService getInstance() {
    return ApplicationManager.getApplication().getService(ModCommandService.class);
  }
}
