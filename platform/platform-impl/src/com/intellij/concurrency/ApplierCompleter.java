// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.ChildContext;
import com.intellij.util.concurrency.Propagation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes {@link #processor} on {@link #array} elements in range from {@link #lo} (inclusive) to {@link #hi} (exclusive).
 * To do this, it starts executing processor on first array items and, if it takes too much time, splits the work and forks the right half.
 * The series of splits lead to a linked list of forked subtasks, each of which is a CountedCompleter of its own,
 * having this task as its parent.
 * After the first pass on the array, this task attempts to steal work from the recently forked off subtasks,
 * by traversing the linked subtasks list, unforking each subtask and calling execAndForkSubTasks() on each recursively.
 * After that, the task completes itself.
 * The process of completing traverses task parent hierarchy, decrementing each pending count until it either
 * decrements not-zero pending count and stops or
 * reaches the top, in which case it invokes {@link ForkJoinTask#quietlyComplete()} which causes the top level task to wake up and join successfully.
 * The exceptions from the subtasks bubble up to the top and are saved in {@link #myThrown}.
 */
final class ApplierCompleter<T> extends ForkJoinTask<Void> {
  private final ApplierCompleter<T>[] myCompleters;
  private final int myIndex;
  private final AtomicReference<Throwable> myThrown;
  private final boolean runInReadAction;
  private final boolean failFastOnAcquireReadAction;
  private final ProgressIndicator progressIndicator;
  private final @NotNull List<? extends T> array;
  private final @NotNull Processor<?> processor;
  private final int lo;
  private final int hi;
  /**
   * number of successfully finished {@link #processArrayItem(int)}s
   */
  private final AtomicInteger finishedOrUnqueuedAllOwned = new AtomicInteger();
  private static final VarHandle booleanArrayHandle = MethodHandles.arrayElementVarHandle(boolean[].class);
  /**
   * {@code processed[i]==true} when the {@link #array}{@code .get(i)} item was processed or being processed by {@link #processor}
   * Access this field via {@link #booleanArrayHandle} volatile-semantic methods only
   */
  private final boolean @NotNull [] processed;

  // if not empty, the read action has failed and this list contains unfinished subtasks
  private final Collection<? super ApplierCompleter<T>> failedSubTasks;
  private final ChildContext childContext;

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    progressIndicator.cancel();
    return super.cancel(mayInterruptIfRunning);
  }

  ApplierCompleter(@NotNull ApplierCompleter<T> @NotNull [] globalCompleters,
                   int myIndex,
                   @NotNull AtomicReference<Throwable> thrown,
                   boolean runInReadAction,
                   boolean failFastOnAcquireReadAction,
                   @NotNull ProgressIndicator progressIndicator,
                   @NotNull List<? extends T> array,
                   boolean @NotNull [] processed,
                   int lo, int hi,
                   @NotNull Collection<? super ApplierCompleter<T>> failedSubTasks,
                   @NotNull Processor<? super T> processor) {
    myCompleters = globalCompleters;
    this.myIndex = myIndex;
    myThrown = thrown;
    this.runInReadAction = runInReadAction;
    this.failFastOnAcquireReadAction = failFastOnAcquireReadAction;
    this.progressIndicator = progressIndicator;
    this.array = array;
    this.processed = processed;
    this.processor = processor;
    this.lo = lo;
    this.hi = hi;
    this.failedSubTasks = failedSubTasks;
    this.childContext = Propagation.createChildContextIgnoreStructuredConcurrency("ApplierCompleter");
  }

  @Override
  public Void getRawResult() {
    return null;
  }

  @Override
  protected void setRawResult(Void value) {
  }

  @Override
  protected boolean exec() {
    Runnable runnable = () -> {
      execAll();
      helpAll();
    };
    wrapAndRun(runnable);
    return true;
  }

  private boolean processArrayItem(int i) {
    boolean r;
    try {
      //noinspection unchecked
      r = ((Processor<Object>)processor).process(array.get(i));
    }
    finally {
      finishedOrUnqueuedAllOwned.incrementAndGet();
    }
    return r;
  }

  private static Throwable moreImportant(Throwable throwable1, Throwable throwable2) {
    Throwable result;
    if (throwable1 == null) {
      result = throwable2;
    }
    else if (throwable2 == null) {
      result = throwable1;
    }
    else {
      // any exception wins over PCE because the latter can be induced by canceled indicator because of the former
      result = throwable1 instanceof ProcessCanceledException ? throwable2 : throwable1;
    }
    return result;
  }
  void wrapAndRun(final @NotNull Runnable process) {
    if (failFastOnAcquireReadAction) {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(()-> wrapInReadActionAndIndicator(process));
    }
    else {
      wrapInReadActionAndIndicator(process);
    }
  }
  private void wrapInReadActionAndIndicator(final @NotNull Runnable process) {
    Runnable toRun = runInReadAction ? () -> {
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(process)) {
        failedSubTasks.add(this);
      }
    } : process;
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator existing = progressManager.getProgressIndicator();
    if (existing == progressIndicator) {
      // we are already wrapped in an indicator - most probably because we came here from helper which steals children tasks
      toRun.run();
    }
    else {
      progressManager.executeProcessUnderProgress(toRun, progressIndicator);
    }
  }

  static final class ComputationAbortedException extends RuntimeException {}
  void execAll() {
    try {
      processArray();
    }
    catch (IndexNotReadyException ignore) {
    }
    catch (Throwable e) {
      accumulateAndRethrowUncheckedRaw(e);
    }
  }
  private void helpAll() {
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      helpOthers();
    }
    catch (IndexNotReadyException ignore) {
    }
    catch (Throwable e) {
      accumulateAndRethrowUncheckedRaw(e);
    }
  }

  // rethrow exception without adding suppressed by, to avoid too long stacktraces which hurt perf
  @Contract("_->fail")
  private void/*Nothing*/ accumulateAndRethrowUncheckedRaw(@NotNull Throwable e) {
    e = accumulateException(myThrown, e);
    cancelProgress();
    rethrowUncheckedRaw(e);
  }

  /**
   * rethrow exception as {@link RuntimeException} or {@link Error}, without calling {@link Throwable#addSuppressed(Throwable)} to avoid too long stacktraces which hurt perf
   */
  @Contract("_->fail")
  static void/*Nothing*/ rethrowUncheckedRaw(@NotNull Throwable e) throws RuntimeException, Error {
    if (e instanceof Error) throw (Error)e;
    if (e instanceof RuntimeException) throw (RuntimeException)e;
    throw new RuntimeException(e);
  }

  static @NotNull Throwable accumulateException(@NotNull AtomicReference<Throwable> thrown, @NotNull Throwable e) {
    Throwable throwable = thrown.accumulateAndGet(e, (throwable1, throwable2) -> moreImportant(throwable1, throwable2));
    return throwable;
  }

  private void processArray() {
    int lo = this.lo;
    int hi = this.hi;
    try (AccessToken ignored = childContext.applyContextActions(true)) {
      for (int i = lo; i < hi && !isFinishedAll(); ++i) {
        ProgressManager.checkCanceled();
        if (unqueue(i) && !processArrayItem(i)) {
          throw new ComputationAbortedException();
        }
      }
    }
    // set to true after processed all items, because some other completer could help processing them in the meantime
  }

  private boolean isFinishedAll() {
    return finishedOrUnqueuedAllOwned.get() == hi - lo;
  }

  private boolean unqueue(int i) {
    return (boolean)booleanArrayHandle.compareAndSet(processed, i, false, true);
  }

  private void helpOthers() {
    for (int i = myIndex == myCompleters.length - 1 ? 0 : myIndex + 1; i != myIndex; i = i == myCompleters.length - 1 ? 0 : i + 1) {
      ApplierCompleter<T> completer = myCompleters[i];
      if (!completer.isFinishedAll()) {
        completer.processArray();
      }
      // else skip entire [completer.lo ... completer.hi] range
    }
  }

  private void cancelProgress() {
    if (!progressIndicator.isCanceled()) {
      progressIndicator.cancel();
    }
  }

  static boolean completeTaskWhichFailToAcquireReadAction(@NotNull List<? extends ApplierCompleter<?>> tasks) {
    if (tasks.isEmpty()) {
      return true;
    }
    // do a defensive copy to avoid CME on SynchronizedList.iterator
    ApplierCompleter<?>[] array = tasks.toArray(new ApplierCompleter[0]);
    final boolean[] result = {true};
    // these tasks could not be executed in the other thread; do them here
    boolean inReadAction = ApplicationManager.getApplication().isReadAccessAllowed(); // we are going to reset thread context here, so the information about locks will be lost
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      for (ApplierCompleter<?> task : array) {
        ProgressManager.checkCanceled();
        Runnable r = () -> {
          task.wrapInReadActionAndIndicator(() -> {
            try {
              task.processArray();
            }
            catch (ComputationAbortedException e) {
              result[0] = false;
            }
          });
        };
        if (inReadAction) {
          r.run();
        }
        else {
          ApplicationManager.getApplication().runReadAction(r);
        }
      }
    }
    return result[0];
  }

  @Override
  public @NonNls String toString() {
    return "(" + lo + "-" + hi + ")" + (isFinishedAll() ? " finished" : "");
  }
}
