/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

import com.intellij.openapi.application.BaseActionRunnable;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {

  private String myName;
  private String myGroupID;
  private Project myProject;

  protected WriteCommandAction(Project project) {
    this(project, "Undefined");
  }

  protected WriteCommandAction(Project project, String commandName) {
    this(project, commandName, null);
  }

  protected WriteCommandAction(final Project project, final String name, final String groupID) {
    myName = name;
    myGroupID = groupID;
    myProject = project;
  }

  public final Project getProject() {
    return myProject;
  }

  public final String getCommandName() {
    return myName;
  }

  public String getGroupID() {
    return myGroupID;
  }

  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<T>(this);

    if (canWriteNow()) {
      return executeCommand(result);
    }

    try {
      if (EventQueue.isDispatchThread()) {
        performWriteCommandAction(result);
      } else {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            performWriteCommandAction(result);
          }
        });
      }
    } catch (Exception e) {
      throw new Error(e);
    }
    return result;
  }

  private void performWriteCommandAction(final RunResult<T> result) {
    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = new RunResult[] {result};

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        getApplication().runWriteAction(new Runnable() {
          public void run() {
            results[0].run();
            results[0] = null;
          }
        });
      }
    }, getCommandName(), getGroupID(), UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
  }

  protected <T> RunResult<T> executeCommand(RunResult<T> result) {
    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = new RunResult[] {result};

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        results[0].run();
        results[0] = null;
      }
    }, getCommandName(), getGroupID(), UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);

    return result;
  }

}

