// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ChildContext;
import com.intellij.util.concurrency.Propagation;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.EDT;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.*;

import java.util.concurrent.*;
import java.util.function.Function;

import static com.intellij.openapi.application.ModalityKt.asContextElement;

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
 */
@Obsolete
public final class ProgressRunner<R> {
  public enum ThreadToUse {
    /**
     * Write Thread with implicit read access and the ability to execute write actions. Can be EDT.
     */
    WRITE,
    /**
     * Arbitrary thread with the ability to execute read actions.
     */
    POOLED,
  }

  private static final Logger LOG = Logger.getInstance(ProgressRunner.class);

  private final @NotNull Function<? super @NotNull ProgressIndicator, ? extends R> myComputation;

  private final boolean isSync;

  private final boolean isModal;

  private final ThreadToUse myThreadToUse;
  private final @NotNull CompletableFuture<? extends @NotNull ProgressIndicator> myProgressIndicatorFuture;

  /**
   * Creates new {@code ProgressRunner} builder instance dedicated to calculating {@code computation}.
   * Does not start computation.
   *
   * @param computation runnable to be executed under progress
   */
  public ProgressRunner(@NotNull Runnable computation) {
    this(__ -> {
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
  public ProgressRunner(@NotNull Function<? super @NotNull ProgressIndicator, ? extends R> computation) {
    this(computation, false, false, ThreadToUse.POOLED, CompletableFuture.completedFuture(new EmptyProgressIndicator()));
  }

  private ProgressRunner(@NotNull Function<? super @NotNull ProgressIndicator, ? extends R> computation,
                         boolean sync,
                         boolean modal,
                         @NotNull ThreadToUse threadToUse,
                         @NotNull CompletableFuture<? extends @NotNull ProgressIndicator> progressIndicatorFuture) {
    myComputation = ClientId.decorateFunction(computation);
    isSync = sync;
    isModal = modal;
    myThreadToUse = threadToUse;
    myProgressIndicatorFuture = progressIndicatorFuture;
  }

  // to avoid abandoning the result
  @Contract(pure = true)
  public @NotNull ProgressRunner<R> sync() {
    return isSync ? this : new ProgressRunner<>(myComputation, true, isModal, myThreadToUse, myProgressIndicatorFuture);
  }

  // to avoid abandoning the result
  @Contract(pure = true)
  public @NotNull ProgressRunner<R> modal() {
    return isModal ? this : new ProgressRunner<>(myComputation, isSync, true, myThreadToUse, myProgressIndicatorFuture);
  }

  /**
   * Specifies thread which should execute computation. Possible values: {@link ThreadToUse#POOLED} and {@link ThreadToUse#WRITE}.
   *
   * @param thread thread to execute computation
   */
  // to avoid abandoning the result
  @Contract(pure = true)
  public @NotNull ProgressRunner<R> onThread(@NotNull ThreadToUse thread) {
    return thread == myThreadToUse ? this : new ProgressRunner<>(myComputation, isSync, isModal, thread, myProgressIndicatorFuture);
  }

  /**
   * Specifies a progress indicator to be associated with computation under progress.
   *
   * @param progressIndicator progress indicator instance
   */
  // to avoid abandoning the result
  @Contract(pure = true)
  public @NotNull ProgressRunner<R> withProgress(@NotNull ProgressIndicator progressIndicator) {
    ProgressIndicator myIndicator;
    try {
      myIndicator = myProgressIndicatorFuture.isDone() ? myProgressIndicatorFuture.get() : null;
    }
    catch (InterruptedException | ExecutionException e) {
      myIndicator = null;
    }
    return progressIndicator.equals(myIndicator)
           ? this
           : new ProgressRunner<>(myComputation, isSync, isModal, myThreadToUse, CompletableFuture.completedFuture(progressIndicator));
  }

  /**
   * Specifies an asynchronous computation which will be used to obtain progress indicator to be associated with computation under progress.
   * The future must return not null indicator.
   *
   * @param progressIndicatorFuture future with progress indicator
   */
  // to avoid abandoning the result
  @Contract(pure = true)
  public @NotNull ProgressRunner<R> withProgress(@NotNull CompletableFuture<? extends @NotNull ProgressIndicator> progressIndicatorFuture) {
    return myProgressIndicatorFuture == progressIndicatorFuture ? this : new ProgressRunner<>(myComputation, isSync, isModal, myThreadToUse, progressIndicatorFuture);
  }

  /**
   * Executes computation with the previously specified environment synchronously.
   *
   * @return a {@link ProgressResult} data class representing the result of computation
   */
  public @NotNull ProgressResult<R> submitAndGet() {
    Future<ProgressResult<R>> future = sync().submit();

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
  public @NotNull CompletableFuture<ProgressResult<R>> submit() {
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

    CompletableFuture<? extends @NotNull ProgressIndicator> progressFuture = myProgressIndicatorFuture.thenApply(progress -> {
      // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
      // does not have a chance to run, and as a result the progress won't be disposed
      if (progress instanceof Disposable) {
        Disposer.register(ApplicationManager.getApplication(), (Disposable)progress);
      }
      return progress;
    });

    CompletableFuture<R> resultFuture;
    if (forceSyncExec) {
      resultFuture = new CompletableFuture<>();
      try {
        ProgressIndicator progressIndicator = progressFuture.join();
        resultFuture.complete(ProgressManager.getInstance().runProcess(() -> myComputation.apply(progressIndicator), progressIndicator));
      }
      catch (Throwable t) {
        resultFuture.completeExceptionally(t);
      }
    }
    else {
      Semaphore modalityEntered = new Semaphore(1);
      Function<@NotNull ProgressIndicator, R> onThreadCallable = (progressIndicator) -> {
        if (isModal) {
          modalityEntered.waitFor();
        }
        // runProcess handles starting/stopping progress and setting thread's current progress
        return ProgressManager.getInstance().runProcess(() -> myComputation.apply(progressIndicator), progressIndicator);
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        resultFuture = execFromEDT(progressFuture, modalityEntered, onThreadCallable);
      }
      else {
        resultFuture = normalExec(progressFuture, modalityEntered, onThreadCallable);
      }
    }

    return resultFuture.handle((result, e) -> {
      Throwable throwable = unwrap(e);
      if (LOG.isDebugEnabled()) {
        if (throwable != null) {
          LOG.debug("ProgressRunner: task completed with throwable", throwable);
        }

        if (isCanceled(progressFuture)) {
          LOG.debug("ProgressRunner: task cancelled");
        }
      }
      return new ProgressResult<>(result, throwable instanceof ProcessCanceledException || isCanceled(progressFuture), throwable);
    });
  }

  // The case of sync exec from the EDT without the ability to poll events (no BlockingProgressIndicator#startBlocking or presence of Write Action)
  // must be handled by very synchronous direct call (alt: use proper progress indicator, i.e. PotemkinProgress or ProgressWindow).
  // Note: running sync task on pooled thread from EDT can lead to deadlock if pooled thread will try to invokeAndWait.
  private boolean checkIfForceDirectExecNeeded() {
    if (!EDT.isCurrentThreadEdt()) {
      return false;
    }
    if (isSync && !ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
      throw new IllegalStateException("Running sync tasks on pure EDT (w/o IW lock) is dangerous for several reasons.");
    }
    if (!isSync && isModal) {
      throw new IllegalStateException("Running async modal tasks from EDT is impossible: modal implies sync dialog show + polling events");
    }
    boolean forceDirectExec = isSync && (ApplicationManager.getApplication().isWriteAccessAllowed() || !isModal);
    if (forceDirectExec) {
      String reason = ApplicationManager.getApplication().isWriteAccessAllowed() ? "inside Write Action" : "not modal execution";
      @NonNls String failedConstraints = "";
      if (isModal) failedConstraints += "Use Modal execution; ";
      if (myThreadToUse == ThreadToUse.POOLED) failedConstraints += "Use pooled thread; ";
      failedConstraints = failedConstraints.isEmpty() ? "none" : failedConstraints;
      Logger.getInstance(ProgressRunner.class)
        .warn("Forced to sync exec on EDT. Reason: " + reason + ". Failed constraints: " + failedConstraints, new Throwable());
    }
    return forceDirectExec;
  }

  private @NotNull CompletableFuture<R> execFromEDT(@NotNull CompletableFuture<? extends @NotNull ProgressIndicator> progressFuture,
                                                    @NotNull Semaphore modalityEntered,
                                                    @NotNull Function<ProgressIndicator, R> onThreadCallable) {
    CompletableFuture<R> taskFuture = launchTask(onThreadCallable, progressFuture);
    CompletableFuture<R> resultFuture;

    if (isModal) {
      // Running task with blocking EDT event pumping has the following contract in test mode:
      //   if a random EDT event processed by blockingPI fails with an exception, the event pumping
      //   is stopped and the submitted task fails with respective exception.
      // The task submitted to e.g. POOLED thread might not be able to finish at all because it requires invoke&waits,
      //   but EDT is broken due an exception. Hence, initial task should be completed exceptionally
      CompletableFuture<Void> blockingRunFuture = progressFuture.thenAccept(progressIndicator -> {
        if (progressIndicator instanceof BlockingProgressIndicator) {
          //noinspection deprecation
          ((BlockingProgressIndicator)progressIndicator).startBlocking(modalityEntered::up, taskFuture);
        }
        else {
          Logger.getInstance(ProgressRunner.class).warn("Can't go modal without BlockingProgressIndicator");
          modalityEntered.up();
        }
      });
      blockingRunFuture.exceptionally(throwable -> {
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
      // At first here was a blocking resultFuture.get() call,
      // which lead to deadlocks when `BlockingProgressIndicator.startBlocking` already exited,
      // and nobody was dispatching EDT events because the current thread (EDT) was blocked on this future,
      // so instead of blocking, we assert that the future should be done at this point,
      // otherwise, `startBlocking` should continue pumping the events to give cancelled tasks a chance to complete.
      if (!resultFuture.isDone()) {
        throw new IllegalStateException("Result future must be done at this point");
      }
    }
    return resultFuture;
  }

  private @NotNull CompletableFuture<R> normalExec(@NotNull CompletableFuture<? extends @NotNull ProgressIndicator> progressFuture,
                                                   @NotNull Semaphore modalityEntered,
                                                   @NotNull Function<@NotNull ProgressIndicator, R> onThreadCallable) {

    if (isModal) {
      Function<ProgressIndicator, ProgressIndicator> modalityRunnable = progressIndicator -> {
        LaterInvocator.enterModal(progressIndicator, (ModalityStateEx)progressIndicator.getModalityState());
        modalityEntered.up();
        return progressIndicator;
      };
      // If a progress indicator has not been calculated yet, grabbing IW lock might lead to deadlock, as progress might need it for init
      progressFuture = progressFuture.thenApplyAsync(modalityRunnable, r -> {
        if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
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
          if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
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

  private static void waitForFutureUnlockingThread(@NotNull CompletableFuture<?> resultFuture) {
    if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
      pollLaterInvocatorActively(resultFuture, LaterInvocator::pollWriteThreadEventsOnce);
      return;
    }
    if (EDT.isCurrentThreadEdt()) {
      throw new UnsupportedOperationException("Sync waiting from EDT is dangerous.");
    }
    try {
      resultFuture.get();
    }
    catch (Throwable ignore) {
    }
  }

  private static void pollLaterInvocatorActively(@NotNull CompletableFuture<?> resultFuture, @NotNull Runnable pollAction) {
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

  public static Throwable unwrap(@Nullable Throwable exception) {
    return exception instanceof CompletionException || exception instanceof ExecutionException ? exception.getCause() : exception;
  }

  private @NotNull CompletableFuture<R> launchTask(
    @NotNull Function<@NotNull ProgressIndicator, R> task,
    @NotNull CompletableFuture<? extends @NotNull ProgressIndicator> progressIndicatorFuture
  ) {
    CompletableFuture<R> resultFuture = new CompletableFuture<>();
    ChildContext childContext = Propagation.createChildContext("ProgressRunner: " + task);
    CoroutineContext context = childContext.getContext();
    Job job = childContext.getJob();
    if (job != null) {
      // cancellation of the Job cancels the future
      job.invokeOnCompletion(true, true, (throwable) -> {
        if (throwable != null) {
          resultFuture.completeExceptionally(throwable);
        }
        return Unit.INSTANCE;
      });
    }
    progressIndicatorFuture.whenComplete((progressIndicator, throwable) -> {
      if (throwable != null) {
        resultFuture.completeExceptionally(throwable);
        return;
      }
      Runnable runnable = new ProgressRunnable<>(resultFuture, task, progressIndicator);
      ContextAwareRunnable contextRunnable = () -> {
        childContext.runInChildContext(() -> {
          CoroutineContext effectiveContext =
            ThreadContext.currentThreadContext().plus(asContextElement(progressIndicator.getModalityState()));
          try (AccessToken ignored = ThreadContext.installThreadContext(effectiveContext, true)) {
            runnable.run();
          }
        });
      };
      switch (myThreadToUse) {
        case POOLED:
          AppExecutorUtil.getAppExecutorService().execute(contextRunnable);
          break;
        case WRITE:
          ModalityState processModality = progressIndicator.getModalityState();
          ApplicationManager.getApplication().invokeLaterOnWriteThread(contextRunnable, processModality);
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + myThreadToUse);
      }
    });
    return resultFuture;
  }

  static class ProgressRunnable<R> implements Runnable {
    private final CompletableFuture<R> resultFuture;
    private final Function<@NotNull ProgressIndicator, R> task;
    private final ProgressIndicator progressIndicator;

    @Async.Schedule
    ProgressRunnable(CompletableFuture<R> future, Function<@NotNull ProgressIndicator, R> task, ProgressIndicator indicator) {
      resultFuture = future;
      this.task = task;
      progressIndicator = indicator;
    }

    @Async.Execute
    @Override
    public void run() {
      try {
        resultFuture.complete(task.apply(progressIndicator));
      }
      catch (Throwable e) {
        resultFuture.completeExceptionally(e);
      }
    }
  }
}
