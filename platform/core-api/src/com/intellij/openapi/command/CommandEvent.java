/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public class CommandEvent extends EventObject {
  private final Runnable myCommand;
  private final Project myProject;
  private final String myCommandName;
  private final Object myCommandGroupId;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;
  private final boolean myShouldRecordActionForActiveDocument;
  private final Document myDocument;

  public CommandEvent(@NotNull CommandProcessor processor, @NotNull Runnable command, Project project, @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, null, null, project, undoConfirmationPolicy);
  }

  public CommandEvent(@NotNull CommandProcessor processor,
                      @NotNull Runnable command,
                      String commandName,
                      Object commandGroupId,
                      Project project,
                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, commandName, commandGroupId, project, undoConfirmationPolicy, true, null);
  }
  public CommandEvent(@NotNull CommandProcessor processor,
                      @NotNull Runnable command,
                      String commandName,
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

  @NotNull
  public CommandProcessor getCommandProcessor() {
    return (CommandProcessor)getSource();
  }

  @NotNull
  public Runnable getCommand() {
    return myCommand;
  }

  public Project getProject() {
    return myProject;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public Object getCommandGroupId() {
    return myCommandGroupId;
  }

  @NotNull
  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  public boolean shouldRecordActionForOriginalDocument() {
    return myShouldRecordActionForActiveDocument;
  }

  public Document getDocument() {
    return myDocument;
  }
}