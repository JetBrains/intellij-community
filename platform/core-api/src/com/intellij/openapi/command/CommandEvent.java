// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class CommandEvent extends EventObject {
  private final Runnable myCommand;
  private final Project myProject;
  private final @NlsContexts.Command String myCommandName;
  private final Object myCommandGroupId;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;
  private final boolean myShouldRecordActionForActiveDocument;
  private final Document myDocument;

  public CommandEvent(@NotNull CommandProcessor processor, @NotNull Runnable command, Project project, @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, null, null, project, undoConfirmationPolicy, true, null);
  }

  public CommandEvent(@NotNull CommandProcessor processor,
                      @NotNull Runnable command,
                      @NlsContexts.Command String commandName,
                      Object commandGroupId,
                      Project project,
                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                      boolean shouldRecordActionForActiveDocument,
                      Document document) {
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

  public Project getProject() {
    return myProject;
  }

  public @NlsContexts.Command String getCommandName() {
    return myCommandName;
  }

  public Object getCommandGroupId() {
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