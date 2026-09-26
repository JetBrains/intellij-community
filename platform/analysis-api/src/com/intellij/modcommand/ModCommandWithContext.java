// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A container of {@link ModCommand} and {@link ActionContext} in which this command should be executed.
 * 
 * @param context command context; can point to the injected file
 * @param command command
 */
public record ModCommandWithContext(@NotNull ActionContext context, @NotNull ModCommand command) {
  /**
   * Executes {@link ModCommand} interactively (may require user input, navigate into editors, etc.).
   * Must be executed inside command (see {@link CommandProcessor}) and without write lock.
   *
   * @param editor context editor (always top-level), if known
   */
  public void executeInteractively(@Nullable Editor editor) {
    ModCommandExecutor.getInstance().executeInteractively(context, command, editor);
  }

  /**
   * Executes given {@link ModCommand} in batch (applies default options, do not navigate)
   * Must be executed inside command (see {@link CommandProcessor}) and without write lock.
   *
   * @return result of execution
   */
  public @NotNull ModCommandExecutor.BatchExecutionResult executeInBatch() {
    return ModCommandExecutor.getInstance().executeInBatch(context, command);
  }
}
