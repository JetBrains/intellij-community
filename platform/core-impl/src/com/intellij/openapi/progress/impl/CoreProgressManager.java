// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.google.common.collect.ConcurrentHashMultiset;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentLongObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class CoreProgressManager extends ProgressManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.impl.CoreProgressManager");

  static final int CHECK_CANCELED_DELAY_MILLIS = 10;
  private final AtomicInteger myUnsafeProgressCount = new AtomicInteger(0);

  public static final boolean ENABLED = !"disabled".equals(System.getProperty("idea.ProcessCanceledException"));
  private static CheckCanceledHook ourCheckCanceledHook;
  private ScheduledFuture<?> myCheckCancelledFuture; // guarded by threadsUnderIndicator

  // indicator -> threads which are running under this indicator.
  // THashMap is avoided here because of tombstones overhead
  private static final Map<ProgressIndicator, Set<Thread>> threadsUnderIndicator = new HashMap<>(); // guarded by threadsUnderIndicator
  // the active indicator for the thread id
  private static final ConcurrentLongObjectMap<ProgressIndicator> currentIndicators = ContainerUtil.createConcurrentLongObjectMap();
  // top-level indicators for the thread id
  private static final ConcurrentLongObjectMap<ProgressIndicator> threadTopLevelIndicators = ContainerUtil.createConcurrentLongObjectMap();
  // threads which are running under canceled indicator
  // THashSet is avoided here because of possible tombstones overhead
  static final Set<Thread> threadsUnderCanceledIndicator = new HashSet<>(); // guarded by threadsUnderIndicator

  @NotNull private static volatile CheckCanceledBehavior ourCheckCanceledBehavior = CheckCanceledBehavior.NONE;
  private enum CheckCanceledBehavior { NONE, ONLY_HOOKS, INDICATOR_PLUS_HOOKS }

  /** active (i.e. which have {@link #executeProcessUnderProgress(Runnable, ProgressIndicator)} method running) indicators
   *  which are not inherited from {@link StandardProgressIndicator}.
   *  for them an extra processing thread (see {@link #myCheckCancelledFuture}) has to be run
   *  to call their non-standard {@link ProgressIndicator#checkCanceled()} method periodically.
   */
  // multiset here (instead of a set) is for simplifying add/remove indicators on process-with-progress start/end with possibly identical indicators.
  private static final Collection<ProgressIndicator> nonStandardIndicators = ConcurrentHashMultiset.create();

  /** true if running in non-cancelable section started with
   * {@link #executeNonCancelableSection(Runnable)} in this thread
   */
  private static final ThreadLocal<Boolean> isInNonCancelableSection = new ThreadLocal<>(); // do not supply initial value to conserve memory

  // must be under threadsUnderIndicator lock
  private void startBackgroundNonStandardIndicatorsPing() {
    if (myCheckCancelledFuture == null) {
      myCheckCancelledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
        for (ProgressIndicator indicator : nonStandardIndicators) {
          try {
            indicator.checkCanceled();
          }
          catch (ProcessCanceledException e) {
            indicatorCanceled(indicator);
          }
        }
      }, 0, CHECK_CANCELED_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }
  }

  // must be under threadsUnderIndicator lock
  private void stopBackgroundNonStandardIndicatorsPing() {
    if (myCheckCancelledFuture != null) {
      myCheckCancelledFuture.cancel(true);
      myCheckCancelledFuture = null;
    }
  }

  @Override
  public void dispose() {
    synchronized (threadsUnderIndicator) {
      stopBackgroundNonStandardIndicatorsPing();
    }
  }

  static boolean isThreadUnderIndicator(@NotNull ProgressIndicator indicator, @NotNull Thread thread) {
    synchronized (threadsUnderIndicator) {
      Set<Thread> threads = threadsUnderIndicator.get(indicator);
      return threads != null && threads.contains(thread);
    }
  }

  List<ProgressIndicator> getCurrentIndicators() {
    synchronized (threadsUnderIndicator) {
      return new ArrayList<>(threadsUnderIndicator.keySet());
    }
  }

  @ApiStatus.Internal
  public static boolean runCheckCanceledHooks(@Nullable ProgressIndicator indicator) {
    CheckCanceledHook hook = ourCheckCanceledHook;
    return hook != null && hook.runHook(indicator);
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    CheckCanceledBehavior behavior = ourCheckCanceledBehavior;
    if (behavior == CheckCanceledBehavior.NONE) return;

    final ProgressIndicator progress = getProgressIndicator();
    if (progress != null && behavior == CheckCanceledBehavior.INDICATOR_PLUS_HOOKS) {
      progress.checkCanceled();
    }
    else {
      runCheckCanceledHooks(progress);
    }
  }

  @Override
  public boolean hasProgressIndicator() {
    return getProgressIndicator() != null;
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    return myUnsafeProgressCount.get() > 0;
  }

  @Override
  public boolean hasModalProgressIndicator() {
    synchronized (threadsUnderIndicator) {
      return ContainerUtil.or(threadsUnderIndicator.keySet(), i -> i.isModal());
    }
  }

  @Override
  public void runProcess(@NotNull final Runnable process, @Nullable ProgressIndicator progress) {
    executeProcessUnderProgress(() -> {
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
    }, progress);
  }

  @Override
  public <T> T runProcess(@NotNull final Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    final Ref<T> ref = new Ref<>();
    runProcess(() -> ref.set(process.compute()), progress);
    return ref.get();
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    if (isInNonCancelableSection()) {
      runnable.run();
    }
    else {
      try {
        isInNonCancelableSection.set(Boolean.TRUE);
        executeProcessUnderProgress(runnable, NonCancelableIndicator.INSTANCE);
      }
      finally {
        isInNonCancelableSection.remove();
      }
    }
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
    final AtomicReference<T> result = new AtomicReference<>();
    final AtomicReference<Throwable> exception = new AtomicReference<>();

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
      ExceptionUtil.rethrowUnchecked(t);
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
        runProcessWithProgressInCurrentThread(task, new EmptyProgressIndicator(), ModalityState.defaultModalityState());
      }
    }
    else if (task.isModal()) {
      runSynchronously(task.asModal());
    }
    else {
      final Task.Backgroundable backgroundable = task.asBackgroundable();
      if (backgroundable.isConditionalModal() && !backgroundable.shouldStartInBackground()) {
        runSynchronously(task);
      }
      else {
        runAsynchronously(backgroundable);
      }
    }
  }

  private void runSynchronously(@NotNull final Task task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runProcessWithProgressSynchronously(task, null);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> runProcessWithProgressSynchronously(task, null));
    }
  }

  private void runAsynchronously(@NotNull final Task.Backgroundable task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runProcessWithProgressAsynchronously(task);
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        Project project = task.getProject();
        if (project != null && project.isDisposed()) {
          LOG.info("Task canceled because of project disposal: " + task);
          finishTask(task, true, null);
          return;
        }

        runProcessWithProgressAsynchronously(task);
      }, ModalityState.defaultModalityState());
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
    return runProcessWithProgressAsynchronously(task, progressIndicator, continuation, progressIndicator.getModalityState());
  }

  @NotNull
  protected TaskRunnable createTaskRunnable(@NotNull Task task,
                                            @NotNull ProgressIndicator indicator,
                                            @Nullable Runnable continuation) {
    return new TaskRunnable(task, indicator, continuation);
  }

  private static class IndicatorDisposable implements Disposable {
    @NotNull private final ProgressIndicator myIndicator;

    IndicatorDisposable(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @Override
    public void dispose() {
      // do nothing if already disposed
      Disposer.dispose((Disposable)myIndicator, false);
    }
  }

  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                        @NotNull final ProgressIndicator progressIndicator,
                                                        @Nullable final Runnable continuation,
                                                        @NotNull final ModalityState modalityState) {
    IndicatorDisposable indicatorDisposable;
    if (progressIndicator instanceof Disposable) {
      // use IndicatorDisposable instead of progressIndicator to
      // avoid re-registering progressIndicator if it was registered on some other parent before
      indicatorDisposable = new IndicatorDisposable(progressIndicator);
      Disposer.register(ApplicationManager.getApplication(), indicatorDisposable);
    }
    else {
      indicatorDisposable = null;
    }

    final Runnable process = createTaskRunnable(task, progressIndicator, continuation);

    TaskContainer action = new TaskContainer(task) {
      @Override
      public void run() {
        boolean processCanceled = false;
        Throwable exception = null;

        final long start = System.currentTimeMillis();
        try {
          runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          processCanceled = true;
        }
        catch (Throwable e) {
          exception = e;
        }
        long elapsed = System.currentTimeMillis() - start;

        boolean finalCanceled = processCanceled || progressIndicator.isCanceled();
        Throwable finalException = exception;

        if (!finalCanceled) {
          notifyTaskFinished(task, elapsed);
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          finishTask(task, finalCanceled, finalException);
          if (indicatorDisposable != null) {
            Disposer.dispose(indicatorDisposable);
          }
        }, modalityState);
      }
    };

    return ApplicationManager.getApplication().executeOnPooledThread(action);
  }

  void notifyTaskFinished(@NotNull Task.Backgroundable task, long elapsed) {
    
  }

  public boolean runProcessWithProgressSynchronously(@NotNull final Task task, @Nullable final JComponent parentComponent) {
    final Ref<Throwable> exceptionRef = new Ref<>();
    TaskContainer taskContainer = new TaskContainer(task) {
      @Override
      public void run() {
        try {
          createTaskRunnable(task, getProgressIndicator(), null).run();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          exceptionRef.set(e);
        }
      }
    };

    ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();
    boolean result = application.runProcessWithProgressSynchronously(taskContainer, task.getTitle(), task.isCancellable(),
                                                                     task.getProject(), parentComponent, task.getCancelText());

    finishTask(task, !result, exceptionRef.get());
    return result;
  }

  public void runProcessWithProgressInCurrentThread(@NotNull final Task task,
                                                    @NotNull final ProgressIndicator progressIndicator,
                                                    @NotNull final ModalityState modalityState) {
    if (progressIndicator instanceof Disposable) {
      Disposer.register(ApplicationManager.getApplication(), (Disposable)progressIndicator);
    }

    final Runnable process = createTaskRunnable(task, progressIndicator, null);

    boolean processCanceled = false;
    Throwable exception = null;
    try {
      runProcess(process, progressIndicator);
    }
    catch (ProcessCanceledException e) {
      processCanceled = true;
    }
    catch (Throwable e) {
      exception = e;
    }

    final boolean finalCanceled = processCanceled || progressIndicator.isCanceled();
    final Throwable finalException = exception;

    if (ApplicationManager.getApplication().isDispatchThread()) {
      finishTask(task, finalCanceled, finalException);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> finishTask(task, finalCanceled, finalException), modalityState);
    }
  }

  protected void finishTask(@NotNull Task task, boolean canceled, @Nullable Throwable error) {
    try {
      if (error != null) {
        task.onThrowable(error);
      }
      else if (canceled) {
        task.onCancel();
      }
      else {
        task.onSuccess();
      }
    }
    finally {
      task.onFinished();
    }
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
    if (progress == null) myUnsafeProgressCount.incrementAndGet();

    try {
      ProgressIndicator oldIndicator = null;
      boolean set = progress != null && progress != (oldIndicator = getProgressIndicator());
      if (set) {
        Thread currentThread = Thread.currentThread();
        long threadId = currentThread.getId();
        setCurrentIndicator(threadId, progress);
        try {
          registerIndicatorAndRun(progress, currentThread, oldIndicator, process);
        }
        finally {
          setCurrentIndicator(threadId, oldIndicator);
        }
      }
      else {
        process.run();
      }
    }
    finally {
      if (progress == null) myUnsafeProgressCount.decrementAndGet();
    }
  }

  @Override
  public boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action, @Nullable ProgressIndicator indicator) {
    ApplicationManager.getApplication().runReadAction(action);
    return true;
  }

  private void registerIndicatorAndRun(@NotNull ProgressIndicator indicator,
                                       @NotNull Thread currentThread,
                                       ProgressIndicator oldIndicator,
                                       @NotNull Runnable process) {
    List<Set<Thread>> threadsUnderThisIndicator = new ArrayList<>();
    synchronized (threadsUnderIndicator) {
      boolean oneOfTheIndicatorsIsCanceled = false;

      for (ProgressIndicator thisIndicator = indicator; thisIndicator != null; thisIndicator = thisIndicator instanceof WrappedProgressIndicator ? ((WrappedProgressIndicator)thisIndicator).getOriginalProgressIndicator() : null) {
        Set<Thread> underIndicator = threadsUnderIndicator.computeIfAbsent(thisIndicator, __ -> new SmartHashSet<>());
        boolean alreadyUnder = !underIndicator.add(currentThread);
        threadsUnderThisIndicator.add(alreadyUnder ? null : underIndicator);

        boolean isStandard = thisIndicator instanceof StandardProgressIndicator;
        if (!isStandard) {
          nonStandardIndicators.add(thisIndicator);
          startBackgroundNonStandardIndicatorsPing();
        }

        oneOfTheIndicatorsIsCanceled |= thisIndicator.isCanceled();
      }

      if (oneOfTheIndicatorsIsCanceled) {
        threadsUnderCanceledIndicator.add(currentThread);
      }
      else {
        threadsUnderCanceledIndicator.remove(currentThread);
      }

      updateShouldCheckCanceled();
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
              stopBackgroundNonStandardIndicatorsPing();
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
        updateShouldCheckCanceled();
      }
    }
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  final void updateShouldCheckCanceled() {
    synchronized (threadsUnderIndicator) {
      CheckCanceledHook hook = createCheckCanceledHook();
      boolean hasCanceledIndicator = !threadsUnderCanceledIndicator.isEmpty();
      ourCheckCanceledHook = hook;
      ourCheckCanceledBehavior = hook == null && !hasCanceledIndicator ? CheckCanceledBehavior.NONE :
                                 hasCanceledIndicator && ENABLED ? CheckCanceledBehavior.INDICATOR_PLUS_HOOKS :
                                 CheckCanceledBehavior.ONLY_HOOKS;
    }
  }

  @Nullable
  protected CheckCanceledHook createCheckCanceledHook() {
    return null;
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
            updateShouldCheckCanceled();
          }
        }
      }
    }
  }

  @TestOnly
  public static boolean isCanceledThread(@NotNull Thread thread) {
    synchronized (threadsUnderIndicator) {
      return threadsUnderCanceledIndicator.contains(thread);
    }
  }

  @Override
  public boolean isInNonCancelableSection() {
    return isInNonCancelableSection.get() != null;
  }

  private static final long MAX_PRIORITIZATION_NANOS = TimeUnit.SECONDS.toNanos(12);
  private static final Thread[] NO_THREADS = new Thread[0];
  private final Set<Thread> myPrioritizedThreads = ContainerUtil.newConcurrentSet();
  private volatile Thread[] myEffectivePrioritizedThreads = NO_THREADS;
  private volatile int myDeprioritizations = 0;
  private final Object myPrioritizationLock = ObjectUtils.sentinel("myPrioritizationLock");
  private volatile long myPrioritizingStarted = 0;

  @Override
  public <T, E extends Throwable> T computePrioritized(@NotNull ThrowableComputable<T, E> computable) throws E {
    Thread thread = Thread.currentThread();

    if (!Registry.is("ide.prioritize.threads") || isPrioritizedThread(thread)) {
      return computable.compute();
    }

    synchronized (myPrioritizationLock) {
      if (myPrioritizedThreads.isEmpty()) {
        myPrioritizingStarted = System.nanoTime();
      }
      myPrioritizedThreads.add(thread);
      updateEffectivePrioritized();
    }
    try {
      return computable.compute();
    }
    finally {
      synchronized (myPrioritizationLock) {
        myPrioritizedThreads.remove(thread);
        updateEffectivePrioritized();
      }
    }
  }

  private void updateEffectivePrioritized() {
    Thread[] prev = myEffectivePrioritizedThreads;
    Thread[] current = myDeprioritizations > 0 || myPrioritizedThreads.isEmpty() ? NO_THREADS : myPrioritizedThreads.toArray(NO_THREADS);
    myEffectivePrioritizedThreads = current;
    if (prev.length == 0 && current.length > 0) {
      prioritizingStarted();
    } else if (prev.length > 0 && current.length == 0) {
      prioritizingFinished();
    }
  }

  protected void prioritizingStarted() {}
  protected void prioritizingFinished() {}

  @ApiStatus.Internal
  public boolean isPrioritizedThread(@NotNull Thread from) {
    return myPrioritizedThreads.contains(from);
  }

  @ApiStatus.Internal
  public void suppressPrioritizing() {
    synchronized (myPrioritizationLock) {
      if (++myDeprioritizations == 100) {
        Attachment attachment = new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString());
        attachment.setIncluded(true);
        LOG.error("A suspiciously high nesting of suppressPrioritizing, forgot to call restorePrioritizing?", attachment);
      }
      updateEffectivePrioritized();
    }
  }

  @ApiStatus.Internal
  public void restorePrioritizing() {
    synchronized (myPrioritizationLock) {
      if (--myDeprioritizations < 0) {
        myDeprioritizations = 0;
        LOG.error("Unmatched suppressPrioritizing/restorePrioritizing");
      }
      updateEffectivePrioritized();
    }
  }

  protected boolean sleepIfNeededToGivePriorityToAnotherThread() {
    if (!isCurrentThreadEffectivelyPrioritized() && checkLowPriorityReallyApplicable()) {
      LockSupport.parkNanos(1_000_000);
      avoidBlockingPrioritizingThread();
      return true;
    }
    return false;
  }

  private boolean isCurrentThreadEffectivelyPrioritized() {
    Thread current = Thread.currentThread();
    for (Thread prioritized : myEffectivePrioritizedThreads) {
      if (prioritized == current) {
        return true;
      }
    }
    return false;
  }

  private boolean checkLowPriorityReallyApplicable() {
    long time = System.nanoTime() - myPrioritizingStarted;
    if (time < 5_000_000) {
      return false; // don't sleep when activities are very short (e.g. empty processing of mouseMoved events)
    }

    if (avoidBlockingPrioritizingThread()) {
      return false;
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      return false; // EDT always has high priority
    }

    if (time > MAX_PRIORITIZATION_NANOS) {
       // Don't wait forever in case someone forgot to stop prioritizing before waiting for other threads to complete
       // wait just for 12 seconds; this will be noticeable (and we'll get 2 thread dumps) but not fatal
      stopAllPrioritization();
      return false;
    }
    return true;
  }

  private boolean avoidBlockingPrioritizingThread() {
    if (isAnyPrioritizedThreadBlocked()) {
      // the current thread could hold a lock that prioritized threads are waiting for
      suppressPrioritizing();
      checkLaterThreadsAreUnblocked();
      return true;
    }
    return false;
  }

  private void checkLaterThreadsAreUnblocked() {
    try {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
        if (isAnyPrioritizedThreadBlocked()) {
          checkLaterThreadsAreUnblocked();
        }
        else {
          restorePrioritizing();
        }
      }, 5, TimeUnit.MILLISECONDS);
    }
    catch (RejectedExecutionException ignore) {
    }
  }

  private void stopAllPrioritization() {
    synchronized (myPrioritizationLock) {
      myPrioritizedThreads.clear();
      updateEffectivePrioritized();
    }
  }

  private boolean isAnyPrioritizedThreadBlocked() {
    for (Thread thread : myEffectivePrioritizedThreads) {
      Thread.State state = thread.getState();
      if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || state == Thread.State.BLOCKED) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static ModalityState getCurrentThreadProgressModality() {
    ProgressIndicator indicator = threadTopLevelIndicators.get(Thread.currentThread().getId());
    ModalityState modality = indicator == null ? null : indicator.getModalityState();
    return modality != null ? modality : ModalityState.NON_MODAL;
  }

  private static void setCurrentIndicator(long threadId, ProgressIndicator indicator) {
    if (indicator == null) {
      currentIndicators.remove(threadId);
      threadTopLevelIndicators.remove(threadId);
    }
    else {
      currentIndicators.put(threadId, indicator);
      if (!threadTopLevelIndicators.containsKey(threadId)) {
        threadTopLevelIndicators.put(threadId, indicator);
      }
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

    @Override
    public String toString() {
      return myTask.toString();
    }
  }

  protected static class TaskRunnable extends TaskContainer {
    private final ProgressIndicator myIndicator;
    private final Runnable myContinuation;

    TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator, @Nullable Runnable continuation) {
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

  @FunctionalInterface
  interface CheckCanceledHook {
    /**
     * @param indicator the indicator whose {@link ProgressIndicator#checkCanceled()} was called, or null if a non-progressive thread performed {@link ProgressManager#checkCanceled()}
     * @return true if the hook has done anything that might take some time.
     */
    boolean runHook(@Nullable ProgressIndicator indicator);
  }

  public static void assertUnderProgress(@NotNull ProgressIndicator indicator) {
    synchronized (threadsUnderIndicator) {
      Set<Thread> threads = threadsUnderIndicator.get(indicator);
      if (threads == null || !threads.contains(Thread.currentThread())) {
        LOG.error("Must be executed under progress indicator: "+indicator+". Please see e.g. ProgressManager.runProcess()");
      }
    }
  }
}
