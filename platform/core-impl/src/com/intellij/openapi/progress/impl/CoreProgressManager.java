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
package com.intellij.openapi.progress.impl;

import com.google.common.collect.ConcurrentHashMultiset;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.containers.ConcurrentLongObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CoreProgressManager extends ProgressManager implements Disposable {
  static final int CHECK_CANCELED_DELAY_MILLIS = 10;
  final AtomicInteger myCurrentUnsafeProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentModalProgressCount = new AtomicInteger(0);

  private static final boolean ENABLED = !"disabled".equals(System.getProperty("idea.ProcessCanceledException"));
  private ScheduledFuture<?> myCheckCancelledFuture; // guarded by threadsUnderIndicator

  // indicator -> threads which are running under this indicator. guarded by threadsUnderIndicator.
  private static final Map<ProgressIndicator, Set<Thread>> threadsUnderIndicator = new THashMap<ProgressIndicator, Set<Thread>>();
  // the active indicator for the thread id
  private static final ConcurrentLongObjectMap<ProgressIndicator> currentIndicators = ContainerUtil.createConcurrentLongObjectMap();
  // threads which are running under canceled indicator
  static final Set<Thread> threadsUnderCanceledIndicator = ContainerUtil.newConcurrentSet();
  private static volatile boolean thereIsProcessUnderCanceledIndicator;

  /** active (i.e. which have {@link #executeProcessUnderProgress(Runnable, ProgressIndicator)} method running) indicators
   *  which are not inherited from {@link StandardProgressIndicator}.
   *  for them an extra processing thread (see {@link #myCheckCancelledFuture}) has to be run
   *  to call their non-standard {@link ProgressIndicator#checkCanceled()} method periodically.
   */
  private static final Collection<ProgressIndicator> nonStandardIndicators = ConcurrentHashMultiset.create();

  @NotNull
  private ScheduledFuture<?> startBackgroundIndicatorPing() {
    return JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        for (ProgressIndicator indicator : nonStandardIndicators) {
          try {
            indicator.checkCanceled();
          }
          catch (ProcessCanceledException e) {
            indicatorCanceled(indicator);
          }
        }
      }
    }, 0, CHECK_CANCELED_DELAY_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void dispose() {
    synchronized (threadsUnderIndicator) {
      if (myCheckCancelledFuture != null) {
        myCheckCancelledFuture.cancel(true);
        myCheckCancelledFuture = null;
      }
    }
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    if (thereIsProcessUnderCanceledIndicator) {
      final ProgressIndicator progress = getProgressIndicator();
      if (progress != null && ENABLED) {
        progress.checkCanceled();
      }
    }
  }

  @Override
  public boolean hasProgressIndicator() {
    return getProgressIndicator() != null;
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    return myCurrentUnsafeProgressCount.get() > 0;
  }

  @Override
  public boolean hasModalProgressIndicator() {
    return myCurrentModalProgressCount.get() > 0;
  }

  @Override
  public void runProcess(@NotNull final Runnable process, final ProgressIndicator progress) {
    executeProcessUnderProgress(new Runnable(){
      @Override
      public void run() {
        try {
          try {
            if (progress != null && !progress.isRunning()) {
              progress.start();
            }
          }
          catch (RuntimeException e) {
            throw e;
          }
          catch (Throwable e) {
            throw new RuntimeException(e);
          }
          process.run();
        }
        finally {
          if (progress != null && progress.isRunning()) {
            progress.stop();
            if (progress instanceof ProgressIndicatorEx) {
              ((ProgressIndicatorEx)progress).processFinish();
            }
          }
        }
      }
    },progress);
  }

  @Override
  public <T> T runProcess(@NotNull final Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    final Ref<T> ref = new Ref<T>();
    runProcess(new Runnable() {
      @Override
      public void run() {
        ref.set(process.compute());
      }
    }, progress);
    return ref.get();
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    executeProcessUnderProgress(runnable, NonCancelableIndicator.INSTANCE);
  }

  @Override
  public void setCancelButtonText(String cancelButtonText) {

  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  @Override
  public <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull final ThrowableComputable<T, E> process,
                                                                        @NotNull @Nls String progressTitle,
                                                                        boolean canBeCanceled,
                                                                        @Nullable Project project) throws E {
    final AtomicReference<T> result = new AtomicReference<T>();
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

    runProcessWithProgressSynchronously(new Task.Modal(project, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          T compute = process.compute();
          result.set(compute);
        }
        catch (Throwable t) {
          exception.set(t);
        }
      }
    }, null);

    Throwable t = exception.get();
    if (t != null) {
      if (t instanceof Error) throw (Error)t;
      if (t instanceof RuntimeException) throw (RuntimeException)t;
      @SuppressWarnings("unchecked") E e = (E)t;
      throw e;
    }

    return result.get();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    Task.Modal task = new Task.Modal(project, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        process.run();
      }
    };
    return runProcessWithProgressSynchronously(task, parentComponent);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable) {
    runProcessWithProgressAsynchronously(project, progressTitle, process, successRunnable, canceledRunnable, PerformInBackgroundOption.DEAF);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable,
                                                   @NotNull PerformInBackgroundOption option) {
    runProcessWithProgressAsynchronously(new Task.Backgroundable(project, progressTitle, true, option) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        process.run();
      }


      @Override
      public void onCancel() {
        if (canceledRunnable != null) {
          canceledRunnable.run();
        }
      }

      @Override
      public void onSuccess() {
        if (successRunnable != null) {
          successRunnable.run();
        }
      }
    });
  }

  @Override
  public void run(@NotNull final Task task) {
    if (task.isHeadless()) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        runProcessWithProgressSynchronously(task, null);
      }
      else {
        new TaskRunnable(task, new EmptyProgressIndicator()).run();
      }
    }
    else if (task.isModal()) {
      runProcessWithProgressSynchronously(task.asModal(), null);
    }
    else {
      final Task.Backgroundable backgroundable = task.asBackgroundable();
      if (backgroundable.isConditionalModal() && !backgroundable.shouldStartInBackground()) {
        runProcessWithProgressSynchronously(task, null);
      }
      else {
        runProcessWithProgressAsynchronously(backgroundable);
      }
    }
  }

  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task) {
    return runProcessWithProgressAsynchronously(task, new EmptyProgressIndicator(), null);
  }

  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                               @NotNull final ProgressIndicator progressIndicator,
                                                               @Nullable final Runnable continuation) {
    return runProcessWithProgressAsynchronously(task, progressIndicator, continuation, ModalityState.NON_MODAL);
  }

  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                               @NotNull final ProgressIndicator progressIndicator,
                                                               @Nullable final Runnable continuation,
                                                               @NotNull final ModalityState modalityState) {
    if (progressIndicator instanceof Disposable) {
      Disposer.register(ApplicationManager.getApplication(), (Disposable)progressIndicator);
    }

    final Runnable process = new TaskRunnable(task, progressIndicator, continuation);

    Runnable action = new TaskContainer(task) {
      @Override
      public void run() {
        boolean canceled = false;
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          canceled = true;
        }

        if (canceled || progressIndicator.isCanceled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onCancel();
            }
          }, modalityState);
        }
        else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onSuccess();
            }
          }, modalityState);
        }
      }
    };

    return ApplicationManager.getApplication().executeOnPooledThread(action);
  }

  protected boolean runProcessWithProgressSynchronously(@NotNull final Task task, @Nullable final JComponent parentComponent) {
    final boolean result = ((ApplicationEx)ApplicationManager.getApplication())
        .runProcessWithProgressSynchronously(new TaskContainer(task) {
          @Override
          public void run() {
            new TaskRunnable(task, ProgressManager.getInstance().getProgressIndicator()).run();
          }
        }, task.getTitle(), task.isCancellable(), task.getProject(), parentComponent, task.getCancelText());
    if (result) {
      task.onSuccess();
    }
    else {
      task.onCancel();
    }
    return result;
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return getCurrentIndicator(Thread.currentThread());
  }

  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    boolean modal = progress != null && progress.isModal();
    if (modal) myCurrentModalProgressCount.incrementAndGet();
    if (progress == null) myCurrentUnsafeProgressCount.incrementAndGet();

    try {
      ProgressIndicator oldIndicator = null;
      boolean set = progress != null && progress != (oldIndicator = getProgressIndicator());
      if (set) {
        Thread currentThread = Thread.currentThread();
        setCurrentIndicator(currentThread, progress);
        try {
          registerIndicatorAndRun(progress, currentThread, oldIndicator, process);
        }
        finally {
          setCurrentIndicator(currentThread, oldIndicator);
        }
      }
      else {
        process.run();
      }
    }
    finally {
      if (progress == null) myCurrentUnsafeProgressCount.decrementAndGet();
      if (modal) myCurrentModalProgressCount.decrementAndGet();
    }
  }

  private void registerIndicatorAndRun(@NotNull ProgressIndicator indicator,
                                       @NotNull Thread currentThread,
                                       ProgressIndicator oldIndicator,
                                       @NotNull Runnable process) {
    List<Set<Thread>> threadsUnderThisIndicator = new ArrayList<Set<Thread>>();
    synchronized (threadsUnderIndicator) {
      for (ProgressIndicator thisIndicator = indicator; thisIndicator != null; thisIndicator = thisIndicator instanceof WrappedProgressIndicator ? ((WrappedProgressIndicator)thisIndicator).getOriginalProgressIndicator() : null) {
        Set<Thread> underIndicator = threadsUnderIndicator.get(thisIndicator);
        if (underIndicator == null) {
          underIndicator = new SmartHashSet<Thread>();
          threadsUnderIndicator.put(thisIndicator, underIndicator);
        }
        boolean alreadyUnder = !underIndicator.add(currentThread);
        threadsUnderThisIndicator.add(alreadyUnder ? null : underIndicator);

        boolean isStandard = thisIndicator instanceof StandardProgressIndicator;
        if (!isStandard) {
          nonStandardIndicators.add(thisIndicator);
          if (myCheckCancelledFuture == null) {
            myCheckCancelledFuture = startBackgroundIndicatorPing();
          }
        }

        if (thisIndicator.isCanceled()) {
          threadsUnderCanceledIndicator.add(currentThread);
        }
        else {
          threadsUnderCanceledIndicator.remove(currentThread);
        }
      }

      thereIsProcessUnderCanceledIndicator = !threadsUnderCanceledIndicator.isEmpty();
    }

    try {
      process.run();
    }
    finally {
      synchronized (threadsUnderIndicator) {
        ProgressIndicator thisIndicator = null;
        // order doesn't matter
        for (int i = 0; i < threadsUnderThisIndicator.size(); i++) {
          thisIndicator = i == 0 ? indicator : ((WrappedProgressIndicator)thisIndicator).getOriginalProgressIndicator();
          Set<Thread> underIndicator = threadsUnderThisIndicator.get(i);
          boolean removed = underIndicator != null && underIndicator.remove(currentThread);
          if (removed && underIndicator.isEmpty()) {
            threadsUnderIndicator.remove(thisIndicator);
          }
          boolean isStandard = thisIndicator instanceof StandardProgressIndicator;
          if (!isStandard) {
            nonStandardIndicators.remove(thisIndicator);
            if (nonStandardIndicators.isEmpty()) {
              myCheckCancelledFuture.cancel(true);
              myCheckCancelledFuture = null;
            }
          }
          // by this time oldIndicator may have been canceled
          if (oldIndicator != null && oldIndicator.isCanceled()) {
            threadsUnderCanceledIndicator.add(currentThread);
          }
          else {
            threadsUnderCanceledIndicator.remove(currentThread);
          }
        }
        thereIsProcessUnderCanceledIndicator = !threadsUnderCanceledIndicator.isEmpty();
      }
    }
  }

  @Override
  protected void indicatorCanceled(@NotNull ProgressIndicator indicator) {
    // mark threads running under this indicator as canceled
    synchronized (threadsUnderIndicator) {
      Set<Thread> threads = threadsUnderIndicator.get(indicator);
      if (threads != null) {
        for (Thread thread : threads) {
          boolean underCancelledIndicator = false;
          for (ProgressIndicator currentIndicator = getCurrentIndicator(thread);
               currentIndicator != null;
               currentIndicator = currentIndicator instanceof WrappedProgressIndicator ?
                                  ((WrappedProgressIndicator)currentIndicator).getOriginalProgressIndicator() : null) {
            if (currentIndicator == indicator) {
              underCancelledIndicator = true;
              break;
            }
          }

          if (underCancelledIndicator) {
            threadsUnderCanceledIndicator.add(thread);
            thereIsProcessUnderCanceledIndicator = true;
          }
        }
      }
    }
  }

  @TestOnly
  public static boolean isCanceledThread(@NotNull Thread thread) {
    return threadsUnderCanceledIndicator.contains(thread);
  }

  @NotNull
  @Override
  public final NonCancelableSection startNonCancelableSection() {
    final ProgressIndicator myOld = ProgressManager.getInstance().getProgressIndicator();

    final Thread currentThread = Thread.currentThread();
    NonCancelableIndicator nonCancelor = new NonCancelableIndicator() {
      @Override
      public void done() {
        setCurrentIndicator(currentThread, myOld);
      }
    };
    setCurrentIndicator(currentThread, nonCancelor);
    return nonCancelor;
  }

  private static void setCurrentIndicator(@NotNull Thread currentThread, ProgressIndicator indicator) {
    if (indicator == null) {
      currentIndicators.remove(currentThread.getId());
    }
    else {
      currentIndicators.put(currentThread.getId(), indicator);
    }
  }
  private static ProgressIndicator getCurrentIndicator(@NotNull Thread thread) {
    return currentIndicators.get(thread.getId());
  }

  protected abstract static class TaskContainer implements Runnable {
    private final Task myTask;

    protected TaskContainer(@NotNull Task task) {
      myTask = task;
    }

    @NotNull
    public Task getTask() {
      return myTask;
    }
  }
  protected static class TaskRunnable extends TaskContainer {
    private final ProgressIndicator myIndicator;
    private final Runnable myContinuation;

    public TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator) {
      this(task, indicator, null);
    }

    public TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator, @Nullable Runnable continuation) {
      super(task);
      myIndicator = indicator;
      myContinuation = continuation;
    }

    @Override
    public void run() {
      try {
        getTask().run(myIndicator);
      }
      finally {
        try {
          if (myIndicator instanceof ProgressIndicatorEx) {
            ((ProgressIndicatorEx)myIndicator).finish(getTask());
          }
        }
        finally {
          if (myContinuation != null) {
            myContinuation.run();
          }
        }
      }
    }
  }
}
