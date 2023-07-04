// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.modcommand.ModCommandAction.ActionContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A support service to execute {@link ModCommand} in context of desktop IDE
 */
public interface ModCommandExecutor {
  /**
   * Executes given {@link ModCommand} interactively (may require user input, navigate into editors, etc.).
   *
   * @param context current context
   * @param command a command to execute
   * @param editor context editor, if known
   */
  @RequiresEdt
  void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor);

  /**
   * Executes given {@link ModCommand} in batch (applies default options, do not navigate)
   *
   * @param context current context
   * @param command a command to execute
   */
  @RequiresEdt
  void executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command);

  /**
   * @return an instance of this service
   */
  static @NotNull ModCommandExecutor getInstance() {
    return ApplicationManager.getApplication().getService(ModCommandExecutor.class);
  }
}
