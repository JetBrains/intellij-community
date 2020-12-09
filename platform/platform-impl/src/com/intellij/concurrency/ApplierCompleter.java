// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountedCompleter;

/**
 * Executes processor on array elements in range from lo (inclusive) to hi (exclusive).
 * To do this it starts executing processor on first array items and, if it takes too much time, splits the work and forks the right half.
 * The series of splits lead to linked list of forked sub tasks, each of which is a CountedCompleter of its own,
 * having this task as its parent.
 * After the first pass on the array, this task attempts to steal work from the recently forked off sub tasks,
 * by traversing the linked subtasks list, unforking each subtask and calling execAndForkSubTasks() on each recursively.
 * After that, the task completes itself.
 * The process of completing traverses task parent hierarchy, decrementing each pending count until it either
 * decrements not-zero pending count and stops or
 * reaches the top, in which case it invokes {@link java.util.concurrent.ForkJoinTask#quietlyComplete()} which causes the top level task to wake up and join successfully.
 * The exceptions from the sub tasks bubble up to the top and saved in {@link #throwable}.
 */
final class ApplierCompleter<T> extends CountedCompleter<Void> {
  private final boolean runInReadAction;
  private final boolean failFastOnAcquireReadAction;
  private final ProgressIndicator progressIndicator;
  @NotNull
  private final List<? extends T> array;
  @NotNull
  private final Processor<? super T> processor;
  private final int lo;
  private final int hi;
  private final ApplierCompleter<T> next; // keeps track of right-hand-side tasks
  volatile Throwable throwable;
  private static final AtomicFieldUpdater<ApplierCompleter, Throwable> throwableUpdater = AtomicFieldUpdater.forFieldOfType(ApplierCompleter.class, Throwable.class);

  // if not null, the read action has failed and this list contains unfinished subtasks
  private final Collection<ApplierCompleter<T>> failedSubTasks;

  //private final List<ApplierCompleter> children = new ArrayList<ApplierCompleter>();

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    progressIndicator.cancel();
    return super.cancel(mayInterruptIfRunning);
  }

  ApplierCompleter(ApplierCompleter<T> parent,
                   boolean runInReadAction,
                   boolean failFastOnAcquireReadAction,
                   @NotNull ProgressIndicator progressIndicator,
                   @NotNull List<? extends T> array,
                   @NotNull Processor<? super T> processor,
                   int lo,
                   int hi,
                   @NotNull Collection<ApplierCompleter<T>> failedSubTasks,
                   ApplierCompleter<T> next) {
    super(parent);
    this.runInReadAction = runInReadAction;
    this.failFastOnAcquireReadAction = failFastOnAcquireReadAction;
    this.progressIndicator = progressIndicator;
    this.array = array;
    this.processor = processor;
    this.lo = lo;
    this.hi = hi;
    this.failedSubTasks = failedSubTasks;
    this.next = next;
  }

  @Override
  public void compute() {
    if (failFastOnAcquireReadAction) {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeByImpatientReader(()-> wrapInReadActionAndIndicator(this::execAndForkSubTasks));
    }
    else {
      wrapInReadActionAndIndicator(this::execAndForkSubTasks);
    }
  }

  private void wrapInReadActionAndIndicator(@NotNull final Runnable process) {
    Runnable toRun = runInReadAction ? () -> {
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(process)) {
        failedSubTasks.add(this);
        doComplete(throwable);
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

  static class ComputationAbortedException extends RuntimeException {}
  // executes tasks one by one and forks right halves if it takes too much time
  // returns the linked list of forked halves - they all need to be joined; null means all tasks have been executed, nothing was forked
  @Nullable
  private ApplierCompleter<T> execAndForkSubTasks() {
    int hi = this.hi;
    ApplierCompleter<T> right = null;
    Throwable throwable = null;

    try {
      for (int i = lo; i < hi; ++i) {
        ProgressManager.checkCanceled();
        if (hi - i >= 2) {
          int availableParallelism = JobSchedulerImpl.getJobPoolParallelism() - Math.max(0,getSurplusQueuedTaskCount());
          if (availableParallelism > 1) {
            // fork off several sub-tasks at once to reduce rampup
            for (int n=0; n<availableParallelism; n++) {
              int mid = i + hi >>> 1;
              if (mid == i || mid == hi) break;
              right = new ApplierCompleter<>(this, runInReadAction, failFastOnAcquireReadAction, progressIndicator, array, processor,
                                             mid, hi, failedSubTasks, right);
              //children.add(right);
              addToPendingCount(1);
              right.fork();
              hi = mid;
            }
          }
        }
        if (!processor.process(array.get(i))) {
          throw new ComputationAbortedException();
        }
      }

      // traverse the list looking for a task available for stealing
      if (right != null) {
        // tries to unfork, execute and re-link subtasks
        ApplierCompleter<T> cur = right;
        Throwable result = right.throwable;
        while (cur != null) {
          ProgressManager.checkCanceled();
          if (cur.tryUnfork()) {
            cur.execAndForkSubTasks();
            result = moreImportant(result, cur.throwable);
          }
          cur = cur.next;
        }
        throwable = result;
      }
    }
    catch (Throwable e) {
      cancelProgress();
      throwable = e;
    }
    finally {
      doComplete(moreImportant(throwable, this.throwable));
    }
    return right;
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

  private void doComplete(Throwable throwable) {
    ApplierCompleter<T> a = this;
    ApplierCompleter<T> child = a;
    while (true) {
      // update parent.throwable in a thread safe way
      Throwable oldThrowable;
      Throwable newThrowable;
      do {
        oldThrowable = a.throwable;
        newThrowable = moreImportant(oldThrowable, throwable);
      } while (oldThrowable != newThrowable && !throwableUpdater.compareAndSet(a, oldThrowable, newThrowable));
      throwable = newThrowable;
      if (a.getPendingCount() == 0) {
        // currently avoid using onExceptionalCompletion since it leaks exceptions via ForkJoinTask.exceptionTable
        a.onCompletion(child);
        //a.onExceptionalCompletion(throwable, child);
        child = a;
        //noinspection unchecked
        a = (ApplierCompleter<T>)a.getCompleter();
        if (a == null) {
          // currently avoid using completeExceptionally since it leaks exceptions via ForkJoinTask.exceptionTable
          child.quietlyComplete();
          break;
        }
      }
      else if (a.decrementPendingCountUnlessZero() != 0) {
        break;
      }
    }
  }

  private void cancelProgress() {
    if (!progressIndicator.isCanceled()) {
      progressIndicator.cancel();
    }
  }

  boolean completeTaskWhichFailToAcquireReadAction() {
    final boolean[] result = {true};
    // these tasks could not be executed in the other thread; do them here
    for (final ApplierCompleter<T> task : failedSubTasks) {
      ProgressManager.checkCanceled();
      ApplicationManager.getApplication().runReadAction(() ->
        task.wrapInReadActionAndIndicator(() -> {
          for (int i = task.lo; i < task.hi; ++i) {
            ProgressManager.checkCanceled();
            if (!task.processor.process(task.array.get(i))) {
              result[0] = false;
              break;
            }
          }
        }));
    }
    return result[0];
  }

  @Override
  @NonNls
  public String toString() {
    return "("+lo+"-"+hi+")"+(getCompleter() == null ? "" : " parent: "+getCompleter());
  }
}
