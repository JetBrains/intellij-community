/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.WriteCommandAction");
  private final String myCommandName;
  private final String myGroupID;
  private final Project myProject;
  private final PsiFile[] myPsiFiles;

  protected WriteCommandAction(@Nullable Project project, /*@NotNull*/ PsiFile... files) {
    this(project, "Undefined", files);
  }

  protected WriteCommandAction(@Nullable Project project, @Nullable @NonNls String commandName, /*@NotNull*/ PsiFile... files) {
    this(project, commandName, null, files);
  }

  protected WriteCommandAction(@Nullable final Project project, @Nullable final String commandName, @Nullable final String groupID, /*@NotNull*/ PsiFile... files) {
    myCommandName = commandName;
    myGroupID = groupID;
    myProject = project;
    if (files == null) {
      LOG.warn("'files' parameter must not be null", new Throwable());
    }
    myPsiFiles = files == null || files.length == 0 ? PsiFile.EMPTY_ARRAY : files;
  }

  public final Project getProject() {
    return myProject;
  }

  public final String getCommandName() {
    return myCommandName;
  }

  public String getGroupID() {
    return myGroupID;
  }

  @NotNull
  @Override
  public RunResult<T> execute() {
    Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && application.isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
      throw new IllegalStateException();
    }
    final RunResult<T> result = new RunResult<T>(this);

    try {
      application.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          performWriteCommandAction(result);
        }
      }, ModalityState.defaultModalityState());
    }
    catch (ProcessCanceledException ignored) { }
    return result;
  }

  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull Collection<PsiFile> psiFiles) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(psiFiles);
  }

  private void performWriteCommandAction(@NotNull RunResult<T> result) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(myPsiFiles))) return;

    // this is needed to prevent memory leak, since the command is put into undo queue
    final RunResult[] results = {result};

    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        AccessToken token = getApplication().acquireWriteActionLock(WriteCommandAction.this.getClass());
        try {
          results[0].run();
          results[0] = null;
        }
        finally {
          token.finish();
        }
      }
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());
  }

  protected boolean isGlobalUndoAction() {
    return false;
  }

  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;
  }

  public void performCommand() throws Throwable {
    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = {new RunResult<T>(this)};
    final Ref<Throwable> exception = new Ref<Throwable>();

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
        exception.set(results[0].run().getThrowable());
        results[0] = null;
      }
    }, getCommandName(), getGroupID(), getUndoConfirmationPolicy());

    Throwable throwable = exception.get();
    if (throwable != null) throw throwable;
  }

  /**
   * WriteCommandAction without result
   */
  public abstract static class Simple<T> extends WriteCommandAction<T> {
    protected Simple(final Project project, /*@NotNull*/ PsiFile... files) {
      super(project, files);
    }
    protected Simple(final Project project, final String commandName, /*@NotNull*/ PsiFile... files) {
      super(project, commandName, files);
    }

    protected Simple(final Project project, final String name, final String groupID, /*@NotNull*/ PsiFile... files) {
      super(project, name, groupID, files);
    }

    @Override
    protected void run(@NotNull final Result<T> result) throws Throwable {
      run();
    }

    protected abstract void run() throws Throwable;
  }

  public static void runWriteCommandAction(Project project, @NotNull final Runnable runnable) {
    runWriteCommandAction(project, "Undefined", null, runnable);
  }

  public static void runWriteCommandAction(Project project,
                                           @Nullable final String commandName,
                                           @Nullable final String groupID,
                                           @NotNull final Runnable runnable,
                                           @NotNull PsiFile... files) {
    new Simple(project, commandName, groupID, files) {
      @Override
      protected void run() throws Throwable {
        runnable.run();
      }
    }.execute();
  }

  public static <T> T runWriteCommandAction(Project project, @NotNull final Computable<T> computable) {
    return new WriteCommandAction<T>(project) {
      @Override
      protected void run(@NotNull Result<T> result) throws Throwable {
        result.setResult(computable.compute());
      }
    }.execute().getResultObject();
  }

  public static <T, E extends Throwable> T runWriteCommandAction(Project project, @NotNull final ThrowableComputable<T, E> computable) throws E {
    RunResult<T> result = new WriteCommandAction<T>(project,"") {
      @Override
      protected void run(@NotNull Result<T> result) throws Throwable {
        result.setResult(computable.compute());
      }
    }.execute();
    if (result.getThrowable() != null) throw (E)result.getThrowable();
    return result.throwException().getResultObject();
  }
}

