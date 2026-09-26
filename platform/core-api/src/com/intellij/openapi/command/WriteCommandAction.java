// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.core.CoreBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.NlsContexts.Command;

/**
 * @see CoroutinesKt#writeCommandAction(Project, String, Function0, Continuation)
 */
public final class WriteCommandAction {

  private WriteCommandAction() {
  }

  private static final String DEFAULT_GROUP_ID = null;

  public interface Builder {
    @Contract(pure = true)
    @NotNull
    Builder withName(@Nullable @Command String name);

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
    private final Collection<? extends PsiElement> myPsiElements;
    private @Command String myCommandName = getDefaultCommandName();
    private String myGroupId = DEFAULT_GROUP_ID;
    private UndoConfirmationPolicy myUndoConfirmationPolicy;
    private boolean myGlobalUndoAction;
    private boolean myShouldRecordActionForActiveDocument = true;

    private BuilderImpl(Project project, @NotNull Collection<? extends PsiElement> elements) {
      myProject = project;
      myPsiElements = elements;
    }

    private BuilderImpl(Project project, PsiElement @NotNull ... elements) {
      myProject = project;
      myPsiElements = Arrays.asList(elements);
    }

    @Override
    public @NotNull Builder withName(@Command String name) {
      myCommandName = name;
      return this;
    }

    @Override
    public @NotNull Builder withGlobalUndo() {
      myGlobalUndoAction = true;
      return this;
    }

    @Override
    public @NotNull Builder shouldRecordActionForActiveDocument(boolean value) {
      myShouldRecordActionForActiveDocument = value;
      return this;
    }

    @Override
    public @NotNull Builder withUndoConfirmationPolicy(@NotNull UndoConfirmationPolicy policy) {
      if (myUndoConfirmationPolicy != null) throw new IllegalStateException("do not call withUndoConfirmationPolicy() several times");
      myUndoConfirmationPolicy = policy;
      return this;
    }

    @Override
    public @NotNull Builder withGroupId(String groupId) {
      myGroupId = groupId;
      return this;
    }

    @Override
    public <E extends Throwable> void run(final @NotNull ThrowableRunnable<E> action) throws E {
      Application application = ApplicationManager.getApplication();
      boolean dispatchThread = application.isDispatchThread();

      if (!dispatchThread && application.holdsReadLock()) {
        throw new IllegalStateException("Must not start write action from within read action in the other thread - deadlock is coming");
      }

      AtomicReference<E> thrown = new AtomicReference<>();
      if (dispatchThread) {
        thrown.set(doRunWriteCommandAction(action));
      }
      else {
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> thrown.set(doRunWriteCommandAction(action)));
        }
        catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) {
        }
      }
      if (thrown.get() != null) {
        throw thrown.get();
      }
    }

    private <E extends Throwable> E doRunWriteCommandAction(@NotNull ThrowableRunnable<E> action) {
      if (!myPsiElements.isEmpty() && !FileModificationService.getInstance().preparePsiElementsForWrite(myPsiElements)) {
        return null;
      }

      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Runnable wrappedRunnable = () -> {
        if (myGlobalUndoAction) {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            action.run();
          }
          catch (Throwable e) {
            thrown.set(e);
          }
        });
      };
      CommandProcessor.getInstance().executeCommand(myProject, wrappedRunnable, myCommandName, myGroupId,
                                                    ObjectUtils.notNull(myUndoConfirmationPolicy, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION),
                                                    myShouldRecordActionForActiveDocument);
      //noinspection unchecked
      return (E)thrown.get();
    }

    @Override
    public <R, E extends Throwable> R compute(final @NotNull ThrowableComputable<R, E> action) throws E {
      AtomicReference<R> result = new AtomicReference<>();
      run(() -> result.set(action.compute()));
      return result.get();
    }
  }

  @Contract(pure = true)
  public static @NotNull Builder writeCommandAction(Project project) {
    return new BuilderImpl(project);
  }

  @Contract(pure = true)
  public static @NotNull Builder writeCommandAction(@NotNull PsiFile first, PsiFile @NotNull ... others) {
    return new BuilderImpl(first.getProject(), ArrayUtil.prepend(first, others));
  }

  @Contract(pure = true)
  public static @NotNull Builder writeCommandAction(Project project, PsiFile @NotNull ... files) {
    return new BuilderImpl(project, files);
  }

  @Contract(pure = true)
  public static @NotNull Builder writeCommandAction(Project project, Collection<? extends PsiElement> elementsToMakeWritable) {
    return new BuilderImpl(project, elementsToMakeWritable);
  }

  /**
   * If run a write command using this method, then the "Undo" action always shows "Undefined" text.
   * <p>
   * Please use {@link #runWriteCommandAction(Project, String, String, Runnable, PsiFile...)} instead.
   */
  @TestOnly
  public static void runWriteCommandAction(Project project, @NotNull Runnable runnable) {
    runWriteCommandAction(project, getDefaultCommandName(), DEFAULT_GROUP_ID, runnable);
  }

  private static @Command String getDefaultCommandName() {
    return CoreBundle.message("command.name.undefined");
  }

  public static void runWriteCommandAction(Project project,
                                           @Nullable @Command String commandName,
                                           @Nullable String groupID,
                                           @NotNull Runnable runnable,
                                           PsiFile @NotNull ... files) {
    writeCommandAction(project, files).withName(commandName).withGroupId(groupID).run(() -> runnable.run());
  }

  public static <T> T runWriteCommandAction(Project project, @NotNull Computable<T> computable) {
    return writeCommandAction(project).compute(() -> computable.compute());
  }

  public static <T, E extends Throwable> T runWriteCommandAction(Project project, @NotNull ThrowableComputable<T, E> computable)
    throws E {
    return writeCommandAction(project).compute(computable);
  }
}