/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.WriteCommandAction");

  private static final String DEFAULT_COMMAND_NAME = "Undefined";
  private static final String DEFAULT_GROUP_ID = null;

  public interface Builder {
    @Contract(pure = true)
    @NotNull
    Builder withName(@Nullable String name);

    @Contract(pure = true)
    @NotNull
    Builder withGroupId(@Nullable String groupId);

    @Contract(pure = true)
    @NotNull
    Builder withUndoConfirmationPolicy(@NotNull UndoConfirmationPolicy policy);

    @Contract(pure = true)
    @NotNull
    Builder withGlobalUndo();

    @Contract(pure = true)
    @NotNull
    Builder shouldRecordActionForActiveDocument(boolean value);

    <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E;

    <R, E extends Throwable> R compute(@NotNull ThrowableComputable<R, E> action) throws E;
  }

  private static class BuilderImpl implements Builder {
    private final Project myProject;
    private final PsiFile[] myFiles;
    private String myCommandName = DEFAULT_COMMAND_NAME;
    private String myGroupId = DEFAULT_GROUP_ID;
    private UndoConfirmationPolicy myPolicy;
    private boolean myGlobalUndoAction;
    private boolean myShouldRecordActionForActiveDocument = true;

    private BuilderImpl(Project project, @NotNull PsiFile... files) {
      myProject = project;
      myFiles = files;
    }

    @NotNull
    @Override
    public Builder withName(String name) {
      myCommandName = name;
      return this;
    }

    @NotNull
    @Override
    public Builder withGlobalUndo() {
      myGlobalUndoAction = true;
      return this;
    }

    @NotNull
    @Override
    public Builder shouldRecordActionForActiveDocument(boolean value) {
      myShouldRecordActionForActiveDocument = value;
      return this;
    }

    @NotNull
    @Override
    public Builder withUndoConfirmationPolicy(@NotNull UndoConfirmationPolicy policy) {
      if (myPolicy != null) throw new IllegalStateException("do not call withUndoConfirmationPolicy() several times");
      myPolicy = policy;
      return this;
    }

    @NotNull
    @Override
    public Builder withGroupId(String groupId) {
      myGroupId = groupId;
      return this;
    }

    @Override
    public <E extends Throwable> void run(@NotNull final ThrowableRunnable<E> action) {
      new MyActionWrap() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          action.run();
        }
      }.execute();
    }

    @Override
    public <R, E extends Throwable> R compute(@NotNull final ThrowableComputable<R, E> action) {
      return new MyActionWrap<R>() {
        @Override
        protected void run(@NotNull Result<R> result) throws Throwable {
          result.setResult(action.compute());
        }
      }.execute().getResultObject();
    }

    private abstract class MyActionWrap<T> extends WriteCommandAction<T> {
      MyActionWrap() {
        super(BuilderImpl.this.myProject, BuilderImpl.this.myCommandName, myGroupId, myFiles);
      }

      @NotNull
      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return ObjectUtils.notNull(myPolicy, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
      }

      @Override
      protected boolean isGlobalUndoAction() {
        return myGlobalUndoAction;
      }

      @Override
      protected boolean shouldRecordActionForActiveDocument() {
        return myShouldRecordActionForActiveDocument;
      }
    }
  }

  @NotNull
  @Contract(pure = true)
  public static Builder writeCommandAction(Project project) {
    return new BuilderImpl(project);
  }

  @NotNull
  @Contract(pure = true)
  public static Builder writeCommandAction(@NotNull PsiFile first, @NotNull PsiFile... others) {
    return new BuilderImpl(first.getProject(), ArrayUtil.prepend(first, others));
  }

  @NotNull
  @Contract(pure = true)
  public static Builder writeCommandAction(Project project, @NotNull PsiFile... files) {
    return new BuilderImpl(project, files);
  }

  private final String myCommandName;
  private final String myGroupID;
  private final Project myProject;
  private final PsiFile[] myPsiFiles;

  /**
   * @deprecated Use {@link #writeCommandAction(Project, PsiFile...)}{@code .run()} instead
   */
  @Deprecated
  protected WriteCommandAction(@Nullable Project project, @NotNull PsiFile... files) {
    this(project, DEFAULT_COMMAND_NAME, files);
  }

  /**
   * @deprecated Use {@link #writeCommandAction(Project, PsiFile...)}{@code .withName(commandName).run()} instead
   */
  @Deprecated
  protected WriteCommandAction(@Nullable Project project, @Nullable String commandName, @NotNull PsiFile... files) {
    this(project, commandName, DEFAULT_GROUP_ID, files);
  }

  /**
   * @deprecated Use {@link #writeCommandAction(Project, PsiFile...)}{@code .withName(commandName).withGroupId(groupID).run()} instead
   */
  @Deprecated
  protected WriteCommandAction(@Nullable Project project,
                               @Nullable String commandName,
                               @Nullable String groupID,
                               @NotNull PsiFile... files) {
    myCommandName = commandName;
    myGroupID = groupID;
    myProject = project;
    myPsiFiles = files.length == 0 ? PsiFile.EMPTY_ARRAY : files;
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

  /**
   * @deprecated Use {@code #writeCommandAction(Project).run()} or compute() instead
   */
  @Deprecated
  @NotNull
  @Override
  public RunResult<T> execute() {
    Application application = ApplicationManager.getApplication();
    boolean dispatchThread = application.isDispatchThread();

    if (!dispatchThread && application.isReadAccessAllowed()) {
      LOG.error("Must not start write action from within read action in the other thread - deadlock is coming");
      throw new IllegalStateException();
    }

    final RunResult<T> result = new RunResult<>(this);
    if (dispatchThread) {
      performWriteCommandAction(result);
    }
    else {
      try {
        TransactionGuard.getInstance().submitTransactionAndWait(() -> performWriteCommandAction(result));
      }
      catch (ProcessCanceledException ignored) {
      }
    }
    return result;
  }

  private void performWriteCommandAction(@NotNull RunResult<T> result) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(myPsiFiles))) return;

    // this is needed to prevent memory leak, since the command is put into undo queue
    final RunResult[] results = {result};

    doExecuteCommand(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        results[0].run();
        results[0] = null;
      });
    });
  }

  /**
   * @deprecated Use {@link #writeCommandAction(Project)}.withGlobalUndo() instead
   */
  @Deprecated
  protected boolean isGlobalUndoAction() {
    return false;
  }

  /**
   * See {@link CommandProcessor#executeCommand(Project, Runnable, String, Object, UndoConfirmationPolicy, boolean)} for details.
   *
   * @deprecated Use {@link #writeCommandAction(Project)}.withUndoConfirmationPolicy() instead
   */
  @Deprecated
  @NotNull
  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;
  }

  /**
   * See {@link CommandProcessor#executeCommand(Project, Runnable, String, Object, UndoConfirmationPolicy, boolean)} for details.
   *
   * @deprecated Use {@link #writeCommandAction(Project)}.shouldRecordActionForActiveDocument() instead
   */
  @Deprecated
  protected boolean shouldRecordActionForActiveDocument() {
    return true;
  }

  /**
   * @deprecated Use {@link CommandProcessor#executeCommand(Project, Runnable, String, Object)} instead
   */
  @Deprecated
  public void performCommand() throws Throwable {
    //this is needed to prevent memory leak, since command
    // is put into undo queue
    final RunResult[] results = {new RunResult<>(this)};
    final Ref<Throwable> exception = new Ref<>();

    doExecuteCommand(() -> {
      exception.set(results[0].run().getThrowable());
      results[0] = null;
    });

    Throwable throwable = exception.get();
    if (throwable != null) throw throwable;
  }

  private void doExecuteCommand(final Runnable runnable) {
    Runnable wrappedRunnable = () -> {
      if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(getProject());
      runnable.run();
    };
    CommandProcessor.getInstance().executeCommand(getProject(), wrappedRunnable, getCommandName(), getGroupID(),
                                                  getUndoConfirmationPolicy(), shouldRecordActionForActiveDocument());
  }

  /**
   * WriteCommandAction without result
   *
   * @deprecated Use {@link #writeCommandAction(Project)}.run() or .compute() instead
   */
  @Deprecated
  public abstract static class Simple<T> extends WriteCommandAction<T> {
    protected Simple(Project project, /*@NotNull*/ PsiFile... files) {
      super(project, files);
    }

    protected Simple(Project project, String commandName, /*@NotNull*/ PsiFile... files) {
      super(project, commandName, files);
    }

    protected Simple(Project project, String name, String groupID, /*@NotNull*/ PsiFile... files) {
      super(project, name, groupID, files);
    }

    @Override
    protected void run(@NotNull Result<T> result) throws Throwable {
      run();
    }

    protected abstract void run() throws Throwable;
  }

  public static void runWriteCommandAction(Project project, @NotNull Runnable runnable) {
    runWriteCommandAction(project, DEFAULT_COMMAND_NAME, DEFAULT_GROUP_ID, runnable);
  }

  public static void runWriteCommandAction(Project project,
                                           @Nullable final String commandName,
                                           @Nullable final String groupID,
                                           @NotNull final Runnable runnable,
                                           @NotNull PsiFile... files) {
    writeCommandAction(project, files).withName(commandName).withGroupId(groupID).run(() -> runnable.run());
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public static <T> T runWriteCommandAction(Project project, @NotNull final Computable<T> computable) {
    return writeCommandAction(project).compute(() -> computable.compute());
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public static <T, E extends Throwable> T runWriteCommandAction(Project project, @NotNull final ThrowableComputable<T, E> computable)
    throws E {
    return writeCommandAction(project).compute(computable);
  }

}