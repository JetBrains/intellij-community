/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommandProcessor {
  public static CommandProcessor getInstance() {
    return ServiceManager.getService(CommandProcessor.class);
  }

  /**
   * @deprecated use {@link #executeCommand(com.intellij.openapi.project.Project, java.lang.Runnable, java.lang.String, java.lang.Object)}
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
   * @param shouldRecordCommandForActiveDocument false if the action is not supposed to be recorded into the currently open document's history.
   *                                             Examples of such actions: Create New File, Change Project Settings etc.
   *                                             Default is true.
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

  public abstract void runUndoTransparentAction(@NotNull Runnable action);

  public abstract boolean isUndoTransparentActionInProgress();

  public abstract void markCurrentCommandAsGlobal(@Nullable Project project);

  public abstract void addAffectedDocuments(@Nullable Project project, @NotNull Document... docs);

  public abstract void addAffectedFiles(@Nullable Project project, @NotNull VirtualFile... files);

  public abstract void addCommandListener(@NotNull CommandListener listener);

  public abstract void addCommandListener(@NotNull CommandListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeCommandListener(@NotNull CommandListener listener);
}
