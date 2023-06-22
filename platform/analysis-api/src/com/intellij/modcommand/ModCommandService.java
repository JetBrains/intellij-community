// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A support service to handle {@link ModCommand} and {@link ModCommandAction}
 */
public interface ModCommandService {
  /**
   * @param action action
   * @return an {@link IntentionAction} wrapper that adapts this action to the old code which requires {@code IntentionAction}.
   * @see ModCommandAction#asIntention() 
   */
  @NotNull IntentionAction wrap(@NotNull ModCommandAction action);

  /**
   * @param action action to wrap
   * @return the action adapted to {@link LocalQuickFix} interface. The adapter is not perfect. In particular,
   * its {@link LocalQuickFix#getName()} simply returns the result of {@link ModCommandAction#getFamilyName()}. If the client
   * of the quick-fix is ModCommand-aware, it can use {@link #unwrap(LocalQuickFix)} to get
   * the action back.
   * @see ModCommandAction#asQuickFix()
   */
  @NotNull LocalQuickFix wrapToQuickFix(@NotNull ModCommandAction action);

  /**
   * @param action {@link IntentionAction}
   * @return a {@link ModCommandAction} which is wrapped inside the supplied intention action; null if the supplied intention action
   * does not wrap a {@code ModCommandAction}.
   */
  @Nullable ModCommandAction unwrap(@NotNull IntentionAction action);

  /**
   * @param fix {@link LocalQuickFix}
   * @return a {@link ModCommandAction} which is wrapped inside the supplied quick-fix; null if the supplied quick-fix
   * does not wrap a {@code ModCommandAction}.
   */
  @Nullable ModCommandAction unwrap(@NotNull LocalQuickFix fix);

  /**
   * Executes given {@link ModCommand} interactively (may require user input, navigate into editors, etc.).
   * 
   * @param project current project
   * @param command a command to execute
   */
  @RequiresEdt
  void executeInteractively(@NotNull Project project, @NotNull ModCommand command);

  /**
   * Executes given {@link ModCommand} in batch (applies default options, do not navigate) 
   *
   * @param project current project
   * @param command a command to execute
   */
  void executeInBatch(@NotNull Project project, @NotNull ModCommand command);

  /**
   * Implementation of ModCommands.psiUpdate; should not be used directly.
   */
  @NotNull ModCommand psiUpdate(@NotNull ModCommandAction.ActionContext context,
                                @NotNull Consumer<@NotNull ModPsiUpdater> updater);

  /**
   * @return an instance of this service
   */
  static @NotNull ModCommandService getInstance() {
    return ApplicationManager.getApplication().getService(ModCommandService.class);
  }
}
