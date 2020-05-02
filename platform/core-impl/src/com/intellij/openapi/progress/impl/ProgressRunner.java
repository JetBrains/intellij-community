// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.EDT;
import com.intellij.codeWithMe.ClientId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
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
  private final Function<? super ProgressIndicator, ? extends R> myComputation;

  private final boolean isSync;

  private final boolean isModal;

  private final ThreadToUse myThreadToUse;
  @Nullable
  private final CompletableFuture<P> myProgressIndicatorFuture;

  /**
   * Creates new {@code ProgressRunner} builder instance dedicated to calculating {@code computation}.
   * Does not start computation.
   *
   * @param computation runnable to be executed under progress
   */
  public ProgressRunner(@NotNull Runnable computation) {
    this(progress -> {
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
    this(progress -> {
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
  public ProgressRunner(@NotNull Function<? super ProgressIndicator, ? extends R> computation) {
    this(computation, false, false, ThreadToUse.POOLED, null);
  }

  private ProgressRunner(@NotNull Function<? super ProgressIndicator, ? extends R> computation,
                         boolean sync,
                         boolean modal, @NotNull ThreadToUse use,
                         @Nullable CompletableFuture<P> progressIndicatorFuture) {
    myComputation = ClientId.decorateFunction(computation);
    isSync = sync;
    isModal = modal;
    myThreadToUse = use;
    myProgressIndicatorFuture = progressIndicatorFuture;
  }

  @NotNull
  public ProgressRunner<R, P> sync() {
    return new ProgressRunner<>(myComputation, true, isModal, myThreadToUse, myProgressIndicatorFuture);
  }

  public ProgressRunner<R, P> modal() {
    return new ProgressRunner<>(myComputation, isSync, true, myThreadToUse, myProgressIndicatorFuture);
  }

  /**
   * Specifies thread which should execute computation. Possible values: {@link ThreadToUse#POOLED} and {@link ThreadToUse#WRITE}.
   *
   * @param thread thread to execute computation
   */
  @NotNull
  public ProgressRunner<R, P> onThread(@NotNull ThreadToUse thread) {
    return new ProgressRunner<>(myComputation, isSync, isModal, thread, myProgressIndicatorFuture);
  }

  /**
   * Specifies a progress indicator to be associated with computation under progress.
   *
   * @param progressIndicator progress indicator instance
   */
  @NotNull
  public <P1 extends ProgressIndicator> ProgressRunner<R, P1> withProgress(@NotNull P1 progressIndicator) {
    return new ProgressRunner<>(myComputation, isSync, isModal, myThreadToUse, CompletableFuture.completedFuture(progressIndicator));
  }

  /**
   * Specifies an asynchronous computation which will be used to obtain progress indicator to be associated with computation under progress.
   *
   * @param progressIndicatorFuture future with progress indicator
   */
  @NotNull
  public <P1 extends ProgressIndicator> ProgressRunner<R, P1> withProgress(@NotNull CompletableFuture<P1> progressIndicatorFuture) {
    return new ProgressRunner<>(myComputation, isSync, isModal, myThreadToUse, progressIndicatorFuture);
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

    boolean forceSyncExec = checkIfForceDirectExecNeeded();

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

    final Semaphore modalityEntered = new Semaphore(forceSyncExec ? 0 : 1);

    Supplier<R> onThreadCallable = () -> {
      Ref<R> result = Ref.create();
      if (isModal) {
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
    if (forceSyncExec) {
      resultFuture = new CompletableFuture<>();
      try {
        resultFuture.complete(onThreadCallable.get());
      }
      catch (Throwable t) {
        resultFuture.completeExceptionally(t);
      }
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      resultFuture = legacyExec(progressFuture, modalityEntered, onThreadCallable);
    }
    else {
      resultFuture = normalExec(progressFuture, modalityEntered, onThreadCallable);
    }

    return resultFuture.handle((result, e) -> {
      Throwable throwable = unwrap(e);
      return new ProgressResult<>(result,
                                  throwable instanceof ProcessCanceledException || isCanceled(progressFuture),
                                  throwable);
    });
  }

  // The case of sync exec from the EDT without the ability to poll events (no BlockingProgressIndicator#startBlocking or presence of Write Action)
  // must be handled by very synchronous direct call (alt: use proper progress indicator, i.e. PotemkinProgress or ProgressWindow).
  // Note: running sync task on pooled thread from EDT can lead to deadlock if pooled thread will try to invokeAndWait.
  private boolean checkIfForceDirectExecNeeded() {
    if (isSync && EDT.isCurrentThreadEdt() && !ApplicationManager.getApplication().isWriteThread()) {
      throw new IllegalStateException("Running sync tasks on pure EDT (w/o IW lock) is dangerous for several reasons.");
    }
    if (!isSync && isModal && EDT.isCurrentThreadEdt()) {
      throw new IllegalStateException("Running async modal tasks from EDT is impossible: modal implies sync dialog show + polling events");
    }

    boolean forceDirectExec = isSync && ApplicationManager.getApplication().isDispatchThread()
                            && (ApplicationManager.getApplication().isWriteAccessAllowed() || !isModal);
    if (forceDirectExec) {
      String reason = ApplicationManager.getApplication().isWriteAccessAllowed() ? "inside Write Action" : "not modal execution";
      String failedConstraints = "";
      if (isModal) failedConstraints += "Use Modal execution; ";
      if (myThreadToUse == ThreadToUse.POOLED) failedConstraints += "Use pooled thread; ";
      failedConstraints = StringUtil.defaultIfEmpty(failedConstraints, "none");
      Logger.getInstance(ProgressRunner.class)
        .warn("Forced to sync exec on EDT. Reason: " + reason + ". Failed constraints: " + failedConstraints, new Throwable());
    }
    return forceDirectExec;
  }

  @NotNull
  private CompletableFuture<R> legacyExec(CompletableFuture<? extends ProgressIndicator> progressFuture,
                                          Semaphore modalityEntered,
                                          Supplier<R> onThreadCallable) {
    final CompletableFuture<R> taskFuture = launchTask(onThreadCallable, progressFuture);
    final CompletableFuture<R> resultFuture;

    if (isModal) {
      // Running task with blocking EDT event pumping has the following contract in test mode:
      //   if a random EDT event processed by blockingPI fails with an exception, the event pumping
      //   is stopped and the submitted task fails with respective exception.
      // The task submitted to e.g. POOLED thread might not be able to finish at all because it requires invoke&waits,
      //   but EDT is broken due an exception. Hence, initial task should be completed exceptionally
      CompletableFuture<Void> blockingRunFuture = progressFuture.thenAccept(progressIndicator -> {
        if (progressIndicator instanceof BlockingProgressIndicator) {
          ((BlockingProgressIndicator)progressIndicator).startBlocking(modalityEntered::up);
        }
        else {
          Logger.getInstance(ProgressRunner.class).warn("Can't go modal without BlockingProgressIndicator");
          modalityEntered.up();
        }
      }).exceptionally(throwable -> {
        taskFuture.completeExceptionally(throwable);
        return null;
      });
      // `startBlocking` might throw unrelated exceptions execute the submitted task so potential failure should be handled.
      // Relates to testMode-ish execution: unhandled exceptions on event pumping are `LOG.error`-ed, hence throw exceptions in tests.
      resultFuture = taskFuture.thenCombine(blockingRunFuture, (r, __) -> r);
    }
    else {
      resultFuture = taskFuture;
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
                                          Supplier<R> onThreadCallable) {
    Function<ProgressIndicator, ProgressIndicator> modalityRunnable = progressIndicator -> {
      LaterInvocator.enterModal(progressIndicator, (ModalityStateEx)progressIndicator.getModalityState());
      modalityEntered.up();
      return progressIndicator;
    };

    if (isModal) {
      // If a progress indicator has not been calculated yet, grabbing IW lock might lead to deadlock, as progress might need it for init
      progressFuture = progressFuture.thenApplyAsync(modalityRunnable, r -> {
        if (ApplicationManager.getApplication().isWriteThread()) {
          r.run();
        }
        else {
          ApplicationManager.getApplication().invokeLaterOnWriteThread(r);
        }
      });
    }

    CompletableFuture<R> resultFuture = launchTask(onThreadCallable, progressFuture);

    if (isModal) {
      CompletableFuture<Void> modalityExitFuture = resultFuture
        .handle((r, throwable) -> r) // ignore result computation exception
        .thenAcceptBoth(progressFuture, (r, progressIndicator) -> {
          if (ApplicationManager.getApplication().isWriteThread()) {
            LaterInvocator.leaveModal(progressIndicator);
          }
          else {
            ApplicationManager.getApplication()
              .invokeLaterOnWriteThread(() -> LaterInvocator.leaveModal(progressIndicator), progressIndicator.getModalityState());
          }
        });

      // It's better to associate task future with modality exit so that future finish will lead to expected state (modality exit)
      resultFuture = resultFuture.thenCombine(modalityExitFuture, (r, __) -> r);
    }

    if (isSync) {
      waitForFutureUnlockingThread(resultFuture);
    }
    return resultFuture;
  }

  private static void waitForFutureUnlockingThread(CompletableFuture<?> resultFuture) {
    if (ApplicationManager.getApplication().isWriteThread()) {
      pollLaterInvocatorActively(resultFuture, LaterInvocator::pollWriteThreadEventsOnce);
    }
    else if (EDT.isCurrentThreadEdt()) {
      throw new UnsupportedOperationException("Sync waiting from pure EDT is dangerous.");
    }
    else {
      try {
        resultFuture.get();
      }
      catch (Throwable ignore) {
      }
    }
  }

  private static void pollLaterInvocatorActively(CompletableFuture<?> resultFuture, @NotNull Runnable pollAction) {
    ApplicationManagerEx.getApplicationEx().runUnlockingIntendedWrite(() -> {
      while (true) {
        try {
          resultFuture.get(10, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ignore) {
          ApplicationManagerEx.getApplicationEx().runIntendedWriteActionOnCurrentThread(pollAction);
          continue;
        }
        catch (Throwable ignored) {
        }
        break;
      }
      return null;
    });
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
  private CompletableFuture<R> launchTask(Supplier<R> callable, CompletableFuture<? extends ProgressIndicator> progressIndicatorFuture) {
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

        progressIndicatorFuture.whenComplete((progressIndicator, throwable) -> {
          if (throwable != null) {
            resultFuture.completeExceptionally(throwable);
            return;
          }
          ModalityState processModality = progressIndicator.getModalityState();
          ApplicationManager.getApplication().invokeLaterOnWriteThread(runnable, processModality);
        });
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + myThreadToUse);
    }
    return resultFuture;
  }
}
