/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

import com.intellij.openapi.project.Project;

import java.util.EventObject;

public class CommandEvent extends EventObject {
  private final Runnable myCommand;
  private final Project myProject;
  private final String myCommandName;
  private final Object myCommandGropupId;
  private final UndoConfirmationPolicy myUndoConfirmationPolicy;

  public CommandEvent(CommandProcessor processor, Runnable command, Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    this(processor, command, null, null, project, undoConfirmationPolicy);
  }

  public CommandEvent(CommandProcessor processor,
                      Runnable command,
                      String commandName,
                      Object commandGropupId,
                      Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    super(processor);
    myCommand = command;
    myCommandName = commandName;
    myCommandGropupId = commandGropupId;
    myProject = project;
    myUndoConfirmationPolicy = undoConfirmationPolicy;
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
    return myCommandGropupId;
  }

  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }
}