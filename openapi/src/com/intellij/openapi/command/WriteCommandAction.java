/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

import com.intellij.openapi.application.BaseActionRunnable;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {
  private final String myName;
  private final String myGroupID;
  private final Project myProject;
  private final PsiFile[] myPsiFiles;

  protected WriteCommandAction(Project project, PsiFile... files) {
    this(project, "Undefined", files);
  }

  protected WriteCommandAction(Project project, String commandName, PsiFile... files) {
    this(project, commandName, null, files);
  }

  protected WriteCommandAction(final Project project, final String name, final String groupID, PsiFile... files) {
    myName = name;
    myGroupID = groupID;
    myProject = project;
    myPsiFiles = files;
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

    if (myPsiFiles.length > 0) {
      List<VirtualFile> list = new SmartList<VirtualFile>();
      for (final PsiFile psiFile : myPsiFiles) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          list.add(virtualFile);
        }
      }
      if (!list.isEmpty()) {
        if (ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(list.toArray(new VirtualFile[list.size()])).hasReadonlyFiles()) {
          return result;
        }
      }
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
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());
  }

  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;
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
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());

    return result;
  }

  /**
   * WriteCommandAction without result
   */
  public static abstract class Simple extends WriteCommandAction {
    protected Simple(final Project project, PsiFile... files) {
      super(project, files);
    }

    protected Simple(final Project project, final String commandName, final PsiFile... files) {
      super(project, commandName, files);
    }

    protected Simple(final Project project, final String name, final String groupID, final PsiFile... files) {
      super(project, name, groupID, files);
    }

    protected void run(final Result result) throws Throwable {
      run();
    }

    protected abstract void run() throws Throwable;
  }
}

