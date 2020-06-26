// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.*;

import java.util.Arrays;

public abstract class WriteCommandAction<T> extends BaseActionRunnable<T> {
  private static final Logger LOG = Logger.getInstance(WriteCommandAction.class);

  private static final String DEFAULT_COMMAND_NAME = "Undefined";
  private static final String DEFAULT_GROUP_ID = null;

  public interface Builder {
    @Contract(pure = true)
    @NotNull
    Builder withName(@Nullable @NlsContexts.Command String name);

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

  private static final class BuilderImpl implements Builder {
    private final Project myProject;
    private final PsiFile[] myFiles;
    private String myCommandName = DEFAULT_COMMAND_NAME;
    private String myGroupId = DEFAULT_GROUP_ID;
    private UndoConfirmationPolicy myPolicy;
    private boolean myGlobalUndoAction;
    private boolean myShouldRecordActionForActiveDocument = true;

    private BuilderImpl(Project project, PsiFile @NotNull ... files) {
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
  public static Builder writeCommandAction(@NotNull PsiFile first, PsiFile @NotNull ... others) {
    return new BuilderImpl(first.getProject(), ArrayUtil.prepend(first, others));
  }

  @NotNull
  @Contract(pure = true)
  public static Builder writeCommandAction(Project project, PsiFile @NotNull ... files) {
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
  protected WriteCommandAction(@Nullable Project project, PsiFile @NotNull ... files) {
    this(project, DEFAULT_COMMAND_NAME, files);
  }

  /**
   * @deprecated Use {@link #writeCommandAction(Project, PsiFile...)}{@code .withName(commandName).run()} instead
   */
  @Deprecated
  protected WriteCommandAction(@Nullable Project project, @Nullable String commandName, PsiFile @NotNull ... files) {
    this(project, commandName, DEFAULT_GROUP_ID, files);
  }

  /**
   * @deprecated Use {@link #writeCommandAction(Project, PsiFile...)}{@code .withName(commandName).withGroupId(groupID).run()} instead
   */
  @Deprecated
  protected WriteCommandAction(@Nullable Project project,
                               @Nullable String commandName,
                               @Nullable String groupID,
                               PsiFile @NotNull ... files) {
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
        ApplicationManager.getApplication().invokeAndWait(() -> performWriteCommandAction(result));
      }
      catch (ProcessCanceledException ignored) {
      }
    }
    return result;
  }

  private void performWriteCommandAction(@NotNull RunResult<T> result) {
    if (myPsiFiles.length > 0 && !FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(myPsiFiles))) {
      return;
    }

    // this is needed to prevent memory leak, since the command is put into undo queue
    Ref<RunResult<?>> resultRef = new Ref<>(result);
    doExecuteCommand(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        resultRef.get().run();
        resultRef.set(null);
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

  /**
   * If run a write command using this method then "Undo" action always shows "Undefined" text - {@link #DEFAULT_COMMAND_NAME}.
   *
   * Please use {@link #runWriteCommandAction(Project, String, String, Runnable, PsiFile...)} instead.
   */
  @TestOnly
  public static void runWriteCommandAction(Project project, @NotNull Runnable runnable) {
    runWriteCommandAction(project, DEFAULT_COMMAND_NAME, DEFAULT_GROUP_ID, runnable);
  }

  public static void runWriteCommandAction(Project project,
                                           @Nls(capitalization = Nls.Capitalization.Title) @Nullable final String commandName,
                                           @Nullable final String groupID,
                                           @NotNull final Runnable runnable,
                                           PsiFile @NotNull ... files) {
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