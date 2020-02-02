// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>
 * A builder-like API for running tasks with {@link ProgressIndicator}.
 * <p>
 * Main goals of this implementation is to:
 * <ul>
 *   <li>Provide a unified way of running tasks under different conditions</li>
 *   <li>Remove dependence on the calling thread for API usage</li>
 *   <li>Streamline extensibility</li>
 *   <li>Encourage asynchronous usage</li>
 * </ul>
 * <h3>Usage</h3>
 * <p>
 * Create a new {@code ProgressRunner} object with a constructor, providing it with a task to execute.
 * Specify execution thread and progress indicator via respective calls {@link #withProgress} and {@link #onThread}, respectively.
 * Submit task and retrieve result as a {@code CompletableFuture} via {@link #submit()} or synchronously as data via {@link #submitAndGet()}
 *
 * @param <R> type of result to be computed by a given task
 * @param <P> type of progress indicator (to be) associated with a given task
 */
public class ProgressRunner<R, P extends ProgressIndicator> {

  public enum ThreadToUse {
    /**
     * Write Thread with implicit read access and the ability to execute write actions. Can be EDT.
     */
    WRITE,
    /**
     * Arbitrary thread with the ability to execute read actions.
     */
    POOLED
  }

  @NotNull
  private final Function<? super ProgressIndicator, R> myComputation;

  private final boolean isSync;

  private final ThreadToUse myThreadToUse;
  @Nullable
  private final BiConsumer<P, Runnable> myBlockEdtRunnable;
  @Nullable
  private final CompletableFuture<P> myProgressIndicatorFuture;

  /**
   * Creates new {@code ProgressRunner} builder instance dedicated to calculating {@code computation}.
   * Does not start computation.
   *
   * @param computation runnable to be executed under progress
   */
  public ProgressRunner(@NotNull Runnable computation) {
    this((progress) -> {
      computation.run();
      return null;
    });
  }

  /**
   * Creates new {@code ProgressRunner} builder instance dedicated to calculating {@code task}.
   * Does not start computation.
   *
   * @param task task to be executed under progress
   */
  public ProgressRunner(@NotNull Task task) {
    this((progress) -> {
      try {
        task.run(progress);
      }
      finally {
        if (progress instanceof ProgressIndicatorEx) {
          ((ProgressIndicatorEx)progress).finish(task);
        }
      }
      return null;
    });
  }

  /**
   * Creates new {@code ProgressRunner} builder instance dedicated to calculating {@code computation}.
   * Does not start computation.
   *
   * @param computation runnable to be executed under progress
   */
  public ProgressRunner(@NotNull Function<? super ProgressIndicator, R> computation) {
    this(computation, false, ThreadToUse.POOLED, null, null);
  }

  private ProgressRunner(@NotNull Function<? super ProgressIndicator, R> computation,
                         boolean sync,
                         @NotNull ThreadToUse use,
                         @Nullable CompletableFuture<P> progressIndicatorFuture,
                         @Nullable BiConsumer<P, Runnable> blockEdtRunnable) {
    myComputation = computation;
    isSync = sync;
    myThreadToUse = use;
    myProgressIndicatorFuture = progressIndicatorFuture;
    myBlockEdtRunnable = blockEdtRunnable;
  }

  @NotNull
  public ProgressRunner<R, P> sync() {
    return new ProgressRunner<>(myComputation, true, myThreadToUse, myProgressIndicatorFuture, myBlockEdtRunnable);
  }

  /**
   * Specifies thread which should execute computation. Possible values: {@link ThreadToUse#POOLED} and {@link ThreadToUse#WRITE}.
   *
   * @param thread thread to execute computation
   */
  @NotNull
  public ProgressRunner<R, P> onThread(@NotNull ThreadToUse thread) {
    return new ProgressRunner<>(myComputation, isSync, thread, myProgressIndicatorFuture, myBlockEdtRunnable);
  }

  /**
   * Specifies a progress indicator to be associated with computation under progress.
   *
   * @param progressIndicator progress indicator instance
   */
  @NotNull
  public <P1 extends ProgressIndicator> ProgressRunner<R, P1> withProgress(@NotNull P1 progressIndicator) {
    return new ProgressRunner<>(myComputation, isSync, myThreadToUse, CompletableFuture.completedFuture(progressIndicator), null);
  }

  /**
   * Specifies an asynchronous computation which will be used to obtain progress indicator to be associated with computation under progress.
   *
   * @param progressIndicatorFuture future with progress indicator
   */
  @NotNull
  public <P1 extends ProgressIndicator> ProgressRunner<R, P1> withProgress(@NotNull CompletableFuture<P1> progressIndicatorFuture) {
    return new ProgressRunner<>(myComputation, isSync, myThreadToUse, progressIndicatorFuture, null);
  }

  /**
   * Specifies an action to be executed to open modal progress window and start pumping EDT events. Only works in case
   * if this progress runner is called from EDT with IW lock obtained.
   *
   * @param blockEdtRunnable modal blocking action, usually {@code ProgressWindow::startBlocking}
   */
  @NotNull
  public ProgressRunner<R, P> withBlockingEdtStart(@NotNull BiConsumer<P, Runnable> blockEdtRunnable) {
    if (myProgressIndicatorFuture == null) {
      throw new IllegalStateException("Blocking with no progress indicator is strange");
    }
    return new ProgressRunner<>(myComputation, isSync, myThreadToUse, myProgressIndicatorFuture, blockEdtRunnable);
  }

  /**
   * Executes computation with the previously specified environment synchronously.
   *
   * @return a {@link ProgressResult} data class representing the result of computation
   */
  @NotNull
  public ProgressResult<R> submitAndGet() {
    CompletableFuture<ProgressResult<R>> future = sync().submit();

    try {
      return future.get();
    }
    catch (Throwable e) {
      throw new AssertionError("submit() handles exceptions and always returns successful future");
    }
  }

  /**
   * Executes computation with the previously specified environment asynchronously, or synchronously
   * if {@link #sync()} was called previously.
   *
   * @return a completable future representing the computation via {@link ProgressResult} data class
   */
  @NotNull
  public CompletableFuture<ProgressResult<R>> submit() {
    /*
    General flow:
    1. Create Progress
    2. (opt) Get on write thread to grab modality
    3. Grab modality
    4. (opt) Release IW
    5. Run/Launch task
    6. (opt) Poll tasks on WT
    */

    CompletableFuture<? extends ProgressIndicator> progressFuture;
    if (myProgressIndicatorFuture == null) {
      progressFuture = CompletableFuture.completedFuture(new EmptyProgressIndicator());
      // TODO It might be interesting to consider using transparent progress indicator substitution in case progress indicator
      //      is not ready yet: e.g. we want to use modal progress indicator, but don't want to wait for UI thread to create it.
    }
    else {
      progressFuture = myProgressIndicatorFuture.thenApply(progress -> {
        // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
        // does not have a chance to run, and as a result the progress won't be disposed
        if (progress instanceof Disposable) {
          Disposer.register(ApplicationManager.getApplication(), (Disposable)progress);
        }
        return progress;
      });
    }

    // 1. If we are on EDT, "the old" rules apply: we have IW lock, and possible writes will only come from a sub-queue which will happen  under new modality
    // 2. If we are on WT, in sync case there will be no possibility to any write to happen because IW lock will be held by this thread
    // 3. If we are on WT, in async case it's possible that write will happen after this task is finished, but _before_ modality was grabbed, so we must wait for it explicitly
    // 4. If we are on Pooled Thread, there were no guarantees anyway.
    boolean shouldWaitForModality = myBlockEdtRunnable != null;
    //                                !SwingUtilities.isEventDispatchThread() &&
    //                                ApplicationManager.getApplication().isDispatchThread() &&
    //                                isAsync;

    final Semaphore modalityEntered = new Semaphore(1);

    Supplier<R> onThreadCallable = () -> {
      Ref<R> result = Ref.create();
      if (shouldWaitForModality) {
        modalityEntered.waitFor();
      }

      // runProcess handles starting/stopping progress and setting thread's current progress
      ProgressIndicator progressIndicator;
      try {
        progressIndicator = progressFuture.get();
      }
      catch (Throwable e) {
        throw new RuntimeException("Can't get progress", e);
      }

      ProgressManager.getInstance().runProcess(() -> result.set(myComputation.apply(progressIndicator)), progressIndicator);
      return result.get();
    };

    final CompletableFuture<R> resultFuture;
    if (SwingUtilities.isEventDispatchThread() && ApplicationManager.getApplication().isDispatchThread()) {
      resultFuture = legacyExec(progressFuture, modalityEntered, onThreadCallable);
    }
    else {
      resultFuture = normalExec(progressFuture, modalityEntered, onThreadCallable, shouldWaitForModality);
    }

    return resultFuture.handle((result, e) -> {
      Throwable throwable = unwrap(e);
      return new ProgressResult<>(result,
                                  throwable instanceof ProcessCanceledException || isCanceled(progressFuture),
                                  throwable);
    });
  }

  @NotNull
  private CompletableFuture<R> legacyExec(CompletableFuture<? extends ProgressIndicator> progressFuture,
                                          Semaphore modalityEntered,
                                          Supplier<R> onThreadCallable) {
    CompletableFuture<R> resultFuture = launchTask(onThreadCallable, myProgressIndicatorFuture);

    if (myBlockEdtRunnable != null) {
      ProgressIndicator progressIndicator;
      try {
        progressIndicator = progressFuture.get();
      }
      catch (Throwable e) {
        throw new RuntimeException("Can't get progress", e);
      }
      //noinspection unchecked
      myBlockEdtRunnable.accept((P)progressIndicator, modalityEntered::up);
    }

    if (isSync) {
      try {
        resultFuture.get();
      }
      catch (Throwable ignore) {
        // ignore possible exceptions, as they will be handled by the subsequent get/whenComplete calls.
      }
    }
    return resultFuture;
  }

  @NotNull
  private CompletableFuture<R> normalExec(CompletableFuture<? extends ProgressIndicator> progressFuture,
                                          Semaphore modalityEntered,
                                          Supplier<R> onThreadCallable, boolean shouldWaitForModality) {
    Runnable modalityRunnable = () -> {
      ProgressIndicator progressIndicator;
      try {
        progressIndicator = progressFuture.get();
      }
      catch (Throwable e) {
        throw new RuntimeException("Can't get progress", e);
      }

      LaterInvocator.enterModal(progressIndicator, (ModalityStateEx)progressIndicator.getModalityState());
      modalityEntered.up();
    };

    if (shouldWaitForModality) {
      if (ApplicationManager.getApplication().isWriteThread()) {
        modalityRunnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLaterOnWriteThread(modalityRunnable);
      }
    }

    final CompletableFuture<R> resultFuture = launchTask(onThreadCallable, myProgressIndicatorFuture);

    resultFuture.whenComplete((r, throwable) -> {
      ProgressIndicator progressIndicator;
      try {
        progressIndicator = progressFuture.get();
      }
      catch (Throwable e) {
        throw new RuntimeException("Can't get progress", e);
      }

      if (shouldWaitForModality) {
        if (ApplicationManager.getApplication().isWriteThread()) {
          LaterInvocator.leaveModal(progressIndicator);
        }
        else {
          ApplicationManager.getApplication()
            .invokeLaterOnWriteThread(() -> LaterInvocator.leaveModal(progressIndicator), progressIndicator.getModalityState());
        }
      }
    });

    if (isSync) {
      if (ApplicationManager.getApplication().isWriteThread()) {
        ApplicationManager.getApplication().runUnlockingIntendedWrite(() -> {
          while (true) {
            try {
              resultFuture.get(10, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException ignore) {
              ApplicationManager.getApplication().runIntendedWriteActionOnCurrentThread(() -> LaterInvocator.pollWriteThreadEventsOnce());
              continue;
            }
            catch (Throwable ignored) {
            }
            break;
          }
          return null;
        });
      }
      else {
        try {
          resultFuture.get();
        }
        catch (Throwable ignore) {
        }
      }
    }
    return resultFuture;
  }

  public static boolean isCanceled(@NotNull Future<? extends ProgressIndicator> progressFuture) {
    try {
      return progressFuture.get().isCanceled();
    }
    catch (Throwable e) {
      return false;
    }
  }

  @Nullable
  public static Throwable unwrap(@Nullable Throwable exception) {
    return exception instanceof CompletionException || exception instanceof ExecutionException ? exception.getCause() : exception;
  }

  @NotNull
  private CompletableFuture<R> launchTask(Supplier<R> callable, CompletableFuture<P> progressIndicatorFuture) {
    final CompletableFuture<R> resultFuture;
    switch (myThreadToUse) {
      case POOLED:
        resultFuture = CompletableFuture.supplyAsync(callable, AppExecutorUtil.getAppExecutorService());
        break;
      case WRITE:
        resultFuture = new CompletableFuture<>();
        Runnable runnable = () -> {
          try {
            resultFuture.complete(callable.get());
          }
          catch (Throwable e) {
            resultFuture.completeExceptionally(e);
          }
        };

        ModalityState processModality;
        try {
          processModality = progressIndicatorFuture.get().getModalityState();
        }
        catch (Throwable e) {
          throw new RuntimeException("Can't get progress or its modality state", e);
        }

        ApplicationManager.getApplication().invokeLaterOnWriteThread(runnable, processModality);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + myThreadToUse);
    }
    return resultFuture;
  }
}
