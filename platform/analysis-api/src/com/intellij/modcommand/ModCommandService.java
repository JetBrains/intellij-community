// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * @param action {@link IntentionAction}
   * @return a {@link ModCommandAction} which is wrapped inside the supplied intention action; null if the supplied intention action
   * does not wrap a {@code ModCommandAction}.
   */
  @Nullable ModCommandAction unwrap(@NotNull IntentionAction action);

  /**
   * Executes given {@link ModCommand}.
   * 
   * @param project current project
   * @param command a command to execute
   */
  @RequiresEdt
  void execute(@NotNull Project project, @NotNull ModCommand command);
}
