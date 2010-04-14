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

import com.intellij.openapi.application.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

    try {
      Runnable runnable = new Runnable() {
        public void run() {
          performWriteCommandAction(result);
        }
      };
      Application application = ApplicationManager.getApplication();
      if (application.isWriteAccessAllowed() || application.isDispatchThread()) {
        runnable.run();
      }
      else {
        GuiUtils.invokeAndWait(runnable);
      }
    } catch (Throwable e) {
      if (e instanceof InvocationTargetException) e = e.getCause();
      if (e instanceof Error) throw (Error)e;
      else if (e instanceof RuntimeException) throw (RuntimeException)e;
      throw new Error(e);
    }
    return result;
  }

  public static boolean ensureFilesWritable(@NotNull final Project project, @NotNull final Collection<PsiFile> psiFiles) {
    if (!psiFiles.isEmpty()) {
      List<VirtualFile> list = new SmartList<VirtualFile>();
      for (final PsiFile psiFile : psiFiles) {
        if (psiFile == null) continue;
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            list.add(virtualFile);
          }
      }
      if (!list.isEmpty()) {
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(VfsUtil.toVirtualFileArray(list)).hasReadonlyFiles()) {
          return false;
        }
      }
    }
    return true;
  }

  private void performWriteCommandAction(final RunResult<T> result) {
    if (myProject != null && !ensureFilesWritable(myProject, myPsiFiles == null? Collections.<PsiFile>emptyList() : Arrays.asList(myPsiFiles))) return;

    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = {result};

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

    protected void run(final Result result) throws Throwable {
      run();
    }

    protected abstract void run() throws Throwable;
  }
}

