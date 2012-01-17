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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;

import java.util.EventObject;

public class CommandEvent extends EventObject {
  private final Runnable myCommand;
  private final Project myProject;
  private final String myCommandName;
  private final Object myCommandGroupId;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;
  private final Document myDocument;

  public CommandEvent(CommandProcessor processor, Runnable command, Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, null, null, project, undoConfirmationPolicy);
  }

  public CommandEvent(CommandProcessor processor,
                      Runnable command,
                      String commandName,
                      Object commandGroupId,
                      Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, commandName, commandGroupId, project, undoConfirmationPolicy, null);
  }
  public CommandEvent(CommandProcessor processor,
                      Runnable command,
                      String commandName,
                      Object commandGroupId,
                      Project project, UndoConfirmationPolicy undoConfirmationPolicy, Document document) {
    super(processor);
    myCommand = command;
    myCommandName = commandName;
    myCommandGroupId = commandGroupId;
    myProject = project;
    myUndoConfirmationPolicy = undoConfirmationPolicy;
    myDocument = document;
  }

  public CommandProcessor getCommandProcessor() {
    return (CommandProcessor)getSource();
  }

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

  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  public Document getDocument() {
    return myDocument;
  }
}