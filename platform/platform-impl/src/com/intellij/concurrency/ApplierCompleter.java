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
package com.intellij.concurrency;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import jsr166e.CountedCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
 * reaches the top, in which case it invokes {@link jsr166e.ForkJoinTask#quietlyComplete()} which causes the top level task to wake up and join successfully.
 * The exceptions from the sub tasks bubble up to the top and saved in {@link #throwable}.
 */
class ApplierCompleter<T> extends CountedCompleter<Void> {
  private final boolean runInReadAction;
  private final ProgressIndicator progressIndicator;
  @NotNull
  private final List<T> array;
  @NotNull
  private final Processor<? super T> processor;
  private final int lo;
  private final int hi;
  private final ApplierCompleter<T> next; // keeps track of right-hand-side tasks
  volatile Throwable throwable;
  private static final AtomicFieldUpdater<ApplierCompleter, Throwable> throwableUpdater = AtomicFieldUpdater.forFieldOfType(ApplierCompleter.class, Throwable.class);

  // if not null, the read action has failed and this list contains unfinished subtasks
  private List<ApplierCompleter<T>> failedSubTasks;

  //private final List<ApplierCompleter> children = new ArrayList<ApplierCompleter>();

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    progressIndicator.cancel();
    return super.cancel(mayInterruptIfRunning);
  }

  ApplierCompleter(ApplierCompleter<T> parent,
                   boolean runInReadAction,
                   @NotNull ProgressIndicator progressIndicator,
                   @NotNull List<T> array,
                   @NotNull Processor<? super T> processor,
                   int lo,
                   int hi,
                   ApplierCompleter<T> next) {
    super(parent);
    this.runInReadAction = runInReadAction;
    this.progressIndicator = progressIndicator;
    this.array = array;
    this.processor = processor;
    this.lo = lo;
    this.hi = hi;
    this.next = next;
  }

  @Override
  public void compute() {
    wrapInReadActionAndIndicator(new Runnable() {
      @Override
      public void run() {
        execAndForkSubTasks();
      }
    });
  }

  private void wrapInReadActionAndIndicator(@NotNull final Runnable process) {
    Runnable toRun = runInReadAction ? new Runnable() {
      @Override
      public void run() {
        if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(process)) {
          failedSubTasks = new ArrayList<ApplierCompleter<T>>();
          failedSubTasks.add(ApplierCompleter.this);
          doComplete(throwable);
        }
      }
    } : process;
    ProgressIndicator existing = ProgressManager.getInstance().getProgressIndicator();
    if (existing == progressIndicator) {
      toRun.run();
    }
    else {
      ProgressManager.getInstance().executeProcessUnderProgress(toRun, progressIndicator);
    }
  }

  static class ComputationAbortedException extends RuntimeException {}
  // executes tasks one by one and forks right halves if it takes too much time
  // returns the linked list of forked halves - they all need to be joined; null means all tasks have been executed, nothing was forked
  @Nullable
  private ApplierCompleter<T> execAndForkSubTasks() {
    int hi = this.hi;
    long start = System.currentTimeMillis();
    ApplierCompleter<T> right = null;
    Throwable throwable = null;
    try {
      for (int i = lo; i < hi; ++i) {
        progressIndicator.checkCanceled();
        if (!processor.process(array.get(i))) {
          throw new ComputationAbortedException();
        }
        long finish = System.currentTimeMillis();
        long elapsed = finish - start;
        if (elapsed > 5 && hi - i >= 2 && getSurplusQueuedTaskCount() <= JobSchedulerImpl.CORES_COUNT) {
          int mid = i + hi >>> 1;
          right = new ApplierCompleter<T>(this, runInReadAction, progressIndicator, array, processor, mid, hi, right);
          //children.add(right);
          addToPendingCount(1);
          right.fork();
          hi = mid;
          start = finish;
        }
      }

      // traverse the list looking for a task available for stealing
      if (right != null) {
        throwable = right.tryToExecAllList();
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
        // currently avoid using onExceptionalCompletion since it leaks exceptions via jsr166e.ForkJoinTask.exceptionTable
        a.onCompletion(child);
        //a.onExceptionalCompletion(throwable, child);
        child = a;
        //noinspection unchecked
        a = (ApplierCompleter)a.getCompleter();
        if (a == null) {
          // currently avoid using completeExceptionally since it leaks exceptions via jsr166e.ForkJoinTask.exceptionTable
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

  // tries to unfork, execute and re-link subtasks
  private Throwable tryToExecAllList() {
    ApplierCompleter<T> right = this;
    Throwable result = throwable;
    while (right != null) {
      if (right.tryUnfork()) {
        right.execAndForkSubTasks();
        result = moreImportant(result, right.throwable);
      }
      right = right.next;
    }
    return result;
  }

  boolean completeTaskWhichFailToAcquireReadAction() {
    if (failedSubTasks == null) {
      return true;
    }
    final boolean[] result = {true};
    // these tasks could not be executed in the other thread; do them here
    for (final ApplierCompleter<T> task : failedSubTasks) {
      task.failedSubTasks = null;
      task.wrapInReadActionAndIndicator(new Runnable() {
        @Override
        public void run() {
          for (int i = task.lo; i < task.hi; ++i) {
            if (!task.processor.process(task.array.get(i))) {
              result[0] = false;
              break;
            }
          }
        }
      });
      if (task.failedSubTasks != null) {
        result[0] = false;
        break;
      }
    }
    return result[0];
  }

  @Override
  public String toString() {
    return System.identityHashCode(this) + " ("+lo+"-"+hi+")";
  }
}
