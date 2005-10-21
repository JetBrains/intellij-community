/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;

import javax.swing.*;
import java.awt.*;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {

  private String myName;
  private Project myProject;

  protected WriteCommandAction(Project project) {
    this(project,"Undefined");
  }

  protected WriteCommandAction(Project project, String commandName) {
    myName = commandName;
    myProject = project;
  }

  public final Project getProject() {
    return myProject;
  }

  public final String getCommandName() {
    return myName;
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
    getApplication().runWriteAction(new Runnable() {
      public void run() {
        executeCommand(result);
      }
    });
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
    }, getCommandName(), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);

    return result;
  }

}

