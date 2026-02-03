// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class CommandEvent extends EventObject {
  private final @NotNull Runnable myCommand;
  private final @Nullable Project myProject;
  private final @Nullable @Command String myCommandName;
  private final @Nullable Object myCommandGroupId;
  private final @NotNull UndoConfirmationPolicy myUndoConfirmationPolicy;
  private final boolean myShouldRecordActionForActiveDocument;
  private final @Nullable Document myDocument;

  public CommandEvent(@NotNull CommandProcessor processor, @NotNull Runnable command, @Nullable Project project, @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, null, null, project, undoConfirmationPolicy, true, null);
  }

  public CommandEvent(@NotNull CommandProcessor processor,
                      @NotNull Runnable command,
                      @Nullable @Command String commandName,
                      @Nullable Object commandGroupId,
                      @Nullable Project project,
                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                      boolean shouldRecordActionForActiveDocument,
                      @Nullable Document document) {
    super(processor);
    myCommand = command;
    myCommandName = commandName;
    myCommandGroupId = commandGroupId;
    myProject = project;
    myUndoConfirmationPolicy = undoConfirmationPolicy;
    myShouldRecordActionForActiveDocument = shouldRecordActionForActiveDocument;
    myDocument = document;
  }

  public @NotNull CommandProcessor getCommandProcessor() {
    return (CommandProcessor)getSource();
  }

  public @NotNull Runnable getCommand() {
    return myCommand;
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  public @Nullable @Command String getCommandName() {
    return myCommandName;
  }

  public @Nullable Object getCommandGroupId() {
    return myCommandGroupId;
  }

  public @NotNull UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  public boolean shouldRecordActionForOriginalDocument() {
    return myShouldRecordActionForActiveDocument;
  }

  public @Nullable Document getDocument() {
    return myDocument;
  }
}