// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A class for defining 'command' scopes. Every undoable change should be executed as part of a command. Commands can nest, in such a case
 * only the outer-most command is taken into account. Commands with the same 'group id' are merged for undo/redo purposes. 'Transparent'
 * actions (commands) are similar to usual commands but don't create a separate undo/redo step - they are undone/redone together with a
 * 'adjacent' non-transparent commands.
 */
public abstract class CommandProcessor {
  public static CommandProcessor getInstance() {
    return ApplicationManager.getApplication().getService(CommandProcessor.class);
  }

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId,
                                      @Nullable Document document);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy confirmationPolicy);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable command,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                                      @Nullable Document document);

  /**
   * @param shouldRecordCommandForActiveDocument {@code false} if the action is not supposed to be recorded into the currently open document's history.
   *                                             Examples of such actions: Create New File, Change Project Settings etc.
   *                                             Default is {@code true}.
   */
  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable command,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                                      boolean shouldRecordCommandForActiveDocument);

  @ApiStatus.Experimental
  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable command,
                                      @Nullable @NlsContexts.Command String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                                      boolean shouldRecordCommandForActiveDocument,
                                      @Nullable Document document);

  public abstract void setCurrentCommandName(@Nullable @NlsContexts.Command String name);

  public abstract void setCurrentCommandGroupId(@Nullable Object groupId);

  @Nullable
  public abstract Runnable getCurrentCommand();

  @Nullable
  @Nls
  public abstract String getCurrentCommandName();

  @Nullable
  public abstract Object getCurrentCommandGroupId();

  @Nullable
  public abstract Project getCurrentCommandProject();

  /**
   * Defines a scope which contains undoable actions, for which there won't be a separate undo/redo step - they will be undone/redone along
   * with 'adjacent' command.
   */
  public abstract void runUndoTransparentAction(@NotNull Runnable action);

  /**
   * @see #runUndoTransparentAction(Runnable)
   */
  public abstract boolean isUndoTransparentActionInProgress();

  public abstract void markCurrentCommandAsGlobal(@Nullable Project project);

  public abstract void addAffectedDocuments(@Nullable Project project, Document @NotNull ... docs);

  public abstract void addAffectedFiles(@Nullable Project project, VirtualFile @NotNull ... files);

  /**
   * Global commands will be merged during {@code action} execution
   */
  @ApiStatus.Experimental
  public abstract void allowMergeGlobalCommands(@NotNull Runnable action);

  /**
   * @deprecated use {@link CommandListener#TOPIC}
   */
  @Deprecated
  public abstract void addCommandListener(@NotNull CommandListener listener);

  /**
   * @deprecated use {@link CommandListener#TOPIC}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public abstract void removeCommandListener(@NotNull CommandListener listener);
}
