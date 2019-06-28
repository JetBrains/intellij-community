// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
    return ServiceManager.getService(CommandProcessor.class);
  }

  /**
   * @deprecated use {@link #executeCommand(Project, Runnable, String, Object)}
   */
  @Deprecated
  public abstract void executeCommand(@NotNull Runnable runnable,
                                      @Nullable String name,
                                      @Nullable Object groupId);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable String name,
                                      @Nullable Object groupId);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @Nullable Document document);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable runnable,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy confirmationPolicy);

  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable command,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy confirmationPolicy,
                                      @Nullable Document document);

  /**
   * @param shouldRecordCommandForActiveDocument {@code false} if the action is not supposed to be recorded into the currently open document's history.
   *                                             Examples of such actions: Create New File, Change Project Settings etc.
   *                                             Default is {@code true}.
   */
  public abstract void executeCommand(@Nullable Project project,
                                      @NotNull Runnable command,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @NotNull UndoConfirmationPolicy confirmationPolicy,
                                      boolean shouldRecordCommandForActiveDocument);

  public abstract void setCurrentCommandName(@Nullable String name);

  public abstract void setCurrentCommandGroupId(@Nullable Object groupId);

  @Nullable
  public abstract Runnable getCurrentCommand();

  @Nullable
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

  public abstract void addAffectedDocuments(@Nullable Project project, @NotNull Document... docs);

  public abstract void addAffectedFiles(@Nullable Project project, @NotNull VirtualFile... files);

  /**
   * @deprecated use {@link CommandListener#TOPIC}
   */
  @Deprecated
  public abstract void addCommandListener(@NotNull CommandListener listener);

  /**
   * @deprecated use {@link CommandListener#TOPIC}
   */
  @Deprecated
  public void addCommandListener(@NotNull CommandListener listener, @NotNull Disposable parentDisposable) {
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(CommandListener.TOPIC, listener);
  }

  /**
   * @deprecated use {@link CommandListener#TOPIC}
   */
  @Deprecated
  public abstract void removeCommandListener(@NotNull CommandListener listener);
}
