// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A support service to execute {@link ModCommand} in the context of a desktop IDE.
 */
public interface ModCommandExecutor {
  /**
   * Executes given {@link ModCommand} interactively (may require user input, navigate into editors, etc.).
   * Must be executed inside command (see {@link CommandProcessor}) and without write lock.
   *
   * @param context current context
   * @param command a command to execute
   * @param editor context editor, if known
   */
  @RequiresEdt
  void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor);

  /**
   * Executes given {@link ModCommand} in batch (applies default options, do not navigate)
   * Must be executed inside command (see {@link CommandProcessor}) and without write lock.
   *
   * @param context current context
   * @param command a command to execute
   * @return result of execution
   */
  @RequiresEdt
  @NotNull BatchExecutionResult executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command);

  /**
   * Apply a command for non-physical file copy.
   *
   * @param command command to apply
   * @param file a non-physical file copy to apply the command to
   * @throws UnsupportedOperationException if the command does something except modifying the specified file
   */
  void executeForFileCopy(@NotNull ModCommand command, @NotNull PsiFile file) throws UnsupportedOperationException;

  /**
   * @param modCommand {@link ModCommand} to generate preview for
   * @param context context in which the action is about to be executed
   * @return default preview for a given ModCommand
   */
  @NotNull
  IntentionPreviewInfo getPreview(@NotNull ModCommand modCommand, @NotNull ActionContext context);

  /**
   * @return an instance of this service
   */
  static @NotNull ModCommandExecutor getInstance() {
    return ApplicationManager.getApplication().getService(ModCommandExecutor.class);
  }

  /**
   * Fetch the command from the supplier in the background and execute it interactively.
   * The action is performed synchronously in EDT and can be canceled by the user if background computation takes a long time.
   * The {@linkplain CommandProcessor command} will be created automatically, if necessary.
   * 
   * @param context action context
   * @param title user-visible title to display if background computation takes a long time.
   * @param editor context editor, if known
   * @param commandSupplier a side-effect-free function to produce a {@link ModCommand}
   */
  @ApiStatus.Experimental
  @RequiresEdt
  static void executeInteractively(@NotNull ActionContext context,
                                   @Nls String title,
                                   @Nullable Editor editor,
                                   @NotNull Supplier<@NotNull ModCommand> commandSupplier) {
    ModCommand command = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(commandSupplier::get).executeSynchronously(),
      title, true, context.project());
    if (!command.isEmpty()) {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      if (commandProcessor.getCurrentCommand() == null) {
        commandProcessor.executeCommand(context.project(), 
                                        () -> getInstance().executeInteractively(context, command, editor), title, null);
      } else {
        getInstance().executeInteractively(context, command, editor);
      }
    }
  }

  /**
   * Result of batch execution
   */
  sealed interface BatchExecutionResult {
    default @NotNull BatchExecutionResult compose(@NotNull BatchExecutionResult next) {
      if (next == Result.NOTHING || next.equals(this) || this instanceof Error || this == Result.CONFLICTS) return this;
      if (this == Result.NOTHING || next instanceof Error || next == Result.CONFLICTS) return next;
      if (this == Result.ABORT || next == Result.ABORT) return Result.ABORT;
      return Result.SUCCESS;
    }

    /**
     * @return message to display in the UI that describes the execution result
     */
    @NotNull @Nls String getMessage();
  }
  
  enum Result implements BatchExecutionResult {
    /**
     * Action was successfully executed.
     */
    SUCCESS,
    /**
     * Action is interactive only, thus produces no effect.
     */
    INTERACTIVE,
    /**
     * Action has no effect.
     */
    NOTHING,
    /**
     * Action was aborted.
     */
    ABORT,
    /**
     * Conflicts should be displayed and confirmed.
     */
    CONFLICTS;

    @Override
    public @Nls @NotNull String getMessage() {
      return switch (this) {
        case SUCCESS -> AnalysisBundle.message("modcommand.result.action.completed.successfully");
        case INTERACTIVE -> AnalysisBundle.message("modcommand.result.action.is.interactive.only.cannot.be.executed.in.batch");
        case NOTHING -> AnalysisBundle.message("modcommand.result.action.has.no.effect");
        case ABORT -> AnalysisBundle.message("modcommand.result.action.was.aborted");
        case CONFLICTS -> AnalysisBundle.message("modcommand.result.conflict");
      };
    }
  }

  /**
   * Indicates that the action execution was unsuccessful.
   * @param message user-readable error message
   */
  record Error(@NotNull @NlsContexts.Tooltip String message) implements BatchExecutionResult {
    @Override
    public @Nls @NotNull String getMessage() {
      return message;
    }
  }
}
