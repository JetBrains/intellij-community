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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.WriteCommandAction");
  private final String myName;
  private final String myGroupID;
  private final Project myProject;
  private final PsiFile[] myPsiFiles;

  protected WriteCommandAction(@Nullable Project project, PsiFile... files) {
    this(project, "Undefined", files);
  }

  protected WriteCommandAction(@Nullable Project project, @Nullable @NonNls String commandName, PsiFile... files) {
    this(project, commandName, null, files);
  }

  protected WriteCommandAction(@Nullable final Project project, @Nullable final String name, @Nullable final String groupID, PsiFile... files) {
    myName = name;
    myGroupID = groupID;
    myProject = project;
    myPsiFiles = files == null || files.length == 0 ? PsiFile.EMPTY_ARRAY : files;
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

  @NotNull
  @Override
  public RunResult<T> execute() {
    if (!ApplicationManager.getApplication().isDispatchThread() && ApplicationManager.getApplication().isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
    }

    final RunResult<T> result = new RunResult<T>(this);

    try {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          performWriteCommandAction(result);
        }
      };
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        runnable.run();
      }
      else if (application.isReadAccessAllowed()) {
        LOG.error("Calling write command from read-action leads to deadlock.");
      }
      else {
        SwingUtilities.invokeAndWait(runnable);
      }
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause()); // save both stacktraces: current & EDT
    }
    catch (InterruptedException ignored) { }
    return result;
  }

  public static boolean ensureFilesWritable(@NotNull final Project project, @NotNull final Collection<PsiFile> psiFiles) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(psiFiles);
  }

  private void performWriteCommandAction(final RunResult<T> result) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(myPsiFiles))) return;

    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = {result};

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            results[0].run();
            results[0] = null;
          }
        });
      }
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());
  }

  protected boolean isGlobalUndoAction() {
    return false;
  }

  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;
  }

  protected <T> RunResult<T> executeCommand(RunResult<T> result) {
    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = {result};

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
        results[0].run();
        results[0] = null;
      }
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());

    return result;
  }

  /**
   * WriteCommandAction without result
   */
  public abstract static class Simple extends WriteCommandAction {
    protected Simple(final Project project, PsiFile... files) {
      super(project, files);
    }

    protected Simple(final Project project, final String commandName, final PsiFile... files) {
      super(project, commandName, files);
    }

    protected Simple(final Project project, final String name, final String groupID, final PsiFile... files) {
      super(project, name, groupID, files);
    }

    @Override
    protected void run(final Result result) throws Throwable {
      run();
    }

    protected abstract void run() throws Throwable;
  }
}

