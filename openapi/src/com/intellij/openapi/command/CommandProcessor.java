/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

public abstract class CommandProcessor {
  public static CommandProcessor getInstance(){
    return ApplicationManager.getApplication().getComponent(CommandProcessor.class);
  }

  /**
   * @deprecated use {@link #executeCommand(com.intellij.openapi.project.Project, java.lang.Runnable, java.lang.String, java.lang.Object)}
   */
  public abstract void executeCommand(Runnable runnable, String name, Object groupId);
  public abstract void executeCommand(Project project, Runnable runnable, String name, Object groupId);
  public abstract void executeCommand(Project project, Runnable runnable, String name, Object groupId, UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void setCurrentCommandName(String name);
  public abstract void setCurrentCommandGroupId(Object groupId);

  public abstract Runnable getCurrentCommand();
  public abstract String getCurrentCommandName();
  public abstract Project getCurrentCommandProject();

  public abstract void addCommandListener(CommandListener listener);
  public abstract void removeCommandListener(CommandListener listener);

  public abstract void runUndoTransparentAction(Runnable action);
  public abstract boolean isUndoTransparentActionInProgress();

  public abstract void markCurrentCommandAsComplex(Project project);
}
