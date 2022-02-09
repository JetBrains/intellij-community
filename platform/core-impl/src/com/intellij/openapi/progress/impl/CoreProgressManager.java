// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentLongObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class CoreProgressManager extends ProgressManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(CoreProgressManager.class);

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

  private enum CheckCanceledBehavior {NONE, ONLY_HOOKS, INDICATOR_PLUS_HOOKS}

  /**
   * active (i.e. which have {@link #executeProcessUnderProgress(Runnable, ProgressIndicator)} method running) indicators
   * which are not inherited from {@link StandardProgressIndicator}.
   * for them an extra processing thread (see {@link #myCheckCancelledFuture}) has to be run
   * to call their non-standard {@link ProgressIndicator#checkCanceled()} method periodically.
   * Poor-man Multiset here (instead of a set) is for simplifying add/remove indicators on process-with-progress start/end with possibly identical indicators.
   * ProgressIndicator -> count of this indicator occurrences in this multiset.
   */
  private static final Map<ProgressIndicator, AtomicInteger> nonStandardIndicators = new ConcurrentHashMap<>();

  /**
   * true if running in non-cancelable section started with
   * {@link #executeNonCancelableSection(Runnable)} in this thread
   */
  private static final ThreadLocal<Boolean> isInNonCancelableSection = new ThreadLocal<>();
    // do not supply initial value to conserve memory

  // must be under threadsUnderIndicator lock
  private void startBackgroundNonStandardIndicatorsPing() {
    if (myCheckCancelledFuture != null) {
      return;
    }

    myCheckCancelledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
      for (ProgressIndicator indicator : nonStandardIndicators.keySet()) {
        try {
          indicator.checkCanceled();
        }
        catch (ProcessCanceledException e) {
          indicatorCanceled(indicator);
        }
      }
    }, 0, CHECK_CANCELED_DELAY_MILLIS, TimeUnit.MILLISECONDS);
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

  @NotNull
  @ApiStatus.Internal
  public static List<ProgressIndicator> getCurrentIndicators() {
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
    if (!isInNonCancelableSection()) {
      Cancellation.checkCancelled();
    }

    CheckCanceledBehavior behavior = ourCheckCanceledBehavior;
    if (behavior == CheckCanceledBehavior.NONE) return;

    if (behavior == CheckCanceledBehavior.INDICATOR_PLUS_HOOKS) {
      ProgressIndicator progress = getProgressIndicator();
      if (progress != null) {
        progress.checkCanceled();
      }
    }
    else {
      runCheckCanceledHooks(null);
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


  // run in current thread
  @Override
  public void runProcess(@NotNull Runnable process, @Nullable ProgressIndicator progress) {
    if (progress != null) {
      assertNoOtherThreadUnder(progress);
    }
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

  private static void assertNoOtherThreadUnder(@NotNull ProgressIndicator progress) {
    synchronized (threadsUnderIndicator) {
      Collection<Thread> threads = threadsUnderIndicator.get(progress);
      Thread other = threads == null || threads.isEmpty() ? null : threads.iterator().next();
      if (other != null) {
        if (other == Thread.currentThread()) {
          LOG.error("This thread is already running under this indicator, starting/stopping it here might be a data race");
        }
        else {
          StringWriter dump = new StringWriter();
          ThreadDumper.dumpCallStack(other, dump, other.getStackTrace());
          LOG.error("Other thread is already running under this indicator, starting/stopping it here might be a data race. Its thread dump:\n" + dump);
        }
      }
    }
  }

  // run in the current thread (?)
  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    computeInNonCancelableSection(() -> {
      runnable.run();
      return null;
    });
  }

  // FROM EDT: bg OR calling if can't
  @Override
  public <T, E extends Exception> T computeInNonCancelableSection(@NotNull ThrowableComputable<T, E> computable) throws E {
    try {
      if (isInNonCancelableSection()) {
        return computable.compute();
      }
      else {
        try {
          isInNonCancelableSection.set(Boolean.TRUE);
          return computeUnderProgress(computable, NonCancelableIndicator.INSTANCE);
        }
        finally {
          isInNonCancelableSection.remove();
        }
      }
    }
    catch (ProcessCanceledException e) {
      throw new RuntimeException("PCE is not expected in non-cancellable section execution", e);
    }
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  @Override
  public <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull ThrowableComputable<T, E> process,
                                                                        @NotNull String progressTitle,
                                                                        boolean canBeCanceled,
                                                                        @Nullable Project project) throws E {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> exception = new AtomicReference<>();

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
    });

    Throwable t = exception.get();
    if (t != null) {
      ExceptionUtil.rethrowUnchecked(t);
      @SuppressWarnings("unchecked") E e = (E)t;
      throw e;
    }

    return result.get();
  }

  // FROM EDT: bg OR calling if can't
  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @NlsContexts.DialogTitle String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    Task.Modal task = new Task.Modal(project, parentComponent, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        process.run();
      }
    };
    return runProcessWithProgressSynchronously(task);
  }

  // bg; runnables on UI/EDT?
  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable,
                                                   @NotNull PerformInBackgroundOption option) {
    runProcessWithProgressAsynchronously(new Task.Backgroundable(project, progressTitle, true, option) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
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

  /**
   * Different places in IntelliJ codebase behaves differently in case of headless mode.
   * <p>
   * Often, they're trying to make async parts synchronous to make it more predictable or controllable.
   * E.g. in tests or IntelliJ-based command line tools this is the usual code:
   * <p>
   * ```
   * if (ApplicationManager.getApplication().isHeadless()) {
   * performSyncChange()
   * }
   * else {
   * scheduleAsyncChange()
   * }
   * ```
   * <p>
   * However, sometimes headless application should behave just as regular GUI Application,
   * with all its asynchronous stuff. For that, the application must declare `intellij.progress.task.ignoreHeadless`
   * system property. And clients should modify its pure `isHeadless` condition to something like
   * <p>
   * ```
   * ApplicationManager.getApplication().isHeadless() && !shouldRunHeadlessTasksAsynchronously()
   * ```
   *
   * @return true is asynchronous tasks must remain asynchronous even in headless mode
   */
  @ApiStatus.Internal
  public static boolean shouldKeepTasksAsynchronousInHeadlessMode() {
    return SystemProperties.getBooleanProperty("intellij.progress.task.ignoreHeadless", false);
  }

  @ApiStatus.Internal
  public static boolean shouldKeepTasksAsynchronous() {
    Application application = ApplicationManager.getApplication();
    boolean isHeadless = application.isUnitTestMode() || application.isHeadlessEnvironment();
    return !isHeadless || shouldKeepTasksAsynchronousInHeadlessMode();
  }

  // from any: bg or current if can't
  @Override
  public void run(@NotNull Task task) {
    if (task.isHeadless() && !shouldKeepTasksAsynchronousInHeadlessMode()) {
      if (SwingUtilities.isEventDispatchThread()) {
        runProcessWithProgressSynchronously(task);
      }
      else {
        runProcessWithProgressInCurrentThread(task, new EmptyProgressIndicator(), ModalityState.defaultModalityState());
      }
    }
    else if (task.isModal()) {
      runProcessWithProgressSynchronously(task.asModal());
    }
    else {
      Task.Backgroundable backgroundable = task.asBackgroundable();
      if (backgroundable.isConditionalModal() && !backgroundable.shouldStartInBackground()) {
        runProcessWithProgressSynchronously(backgroundable);
      }
      else {
        runAsynchronously(backgroundable);
      }
    }
  }

  // from any: bg
  private void runAsynchronously(@NotNull Task.Backgroundable task) {
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
  protected ProgressIndicator createDefaultAsynchronousProgressIndicator(@NotNull Task.Backgroundable task) {
    return new EmptyProgressIndicator();
  }

  // from any: bg
  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task) {
    return runProcessWithProgressAsynchronously(task, createDefaultAsynchronousProgressIndicator(task), null);
  }

  // from any: bg
  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task,
                                                        @NotNull ProgressIndicator progressIndicator,
                                                        @Nullable Runnable continuation) {
    return runProcessWithProgressAsynchronously(task, progressIndicator, continuation, progressIndicator.getModalityState());
  }

  @Deprecated
  protected void startTask(@NotNull Task task,
                           @NotNull ProgressIndicator indicator,
                           @Nullable Runnable continuation) {

    try {
      task.run(indicator);
    }
    finally {
      try {
        if (indicator instanceof ProgressIndicatorEx) {
          ((ProgressIndicatorEx)indicator).finish(task);
        }
      }
      finally {
        if (continuation != null) {
          continuation.run();
        }
      }
    }
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

  // from any: bg, task.finish on "UI/EDT"
  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task,
                                                        @NotNull ProgressIndicator progressIndicator,
                                                        @Nullable Runnable continuation,
                                                        @NotNull ModalityState modalityState) {
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

    AtomicLong elapsed = new AtomicLong();
    return new ProgressRunner<>(progress -> {
      long start = System.currentTimeMillis();
      try {
        startTask(task, progress, continuation);
      }
      finally {
        elapsed.set(System.currentTimeMillis() - start);
      }
      return null;
    }).onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(progressIndicator)
      .submit()
      .whenComplete(ClientId.decorateBiConsumer((result, err) -> {
        if (!result.isCanceled()) {
          notifyTaskFinished(task, elapsed.get());
        }

        ApplicationUtil.invokeLaterSomewhere(task.whereToRunCallbacks(), modalityState, () -> {
          finishTask(task, result.isCanceled(), result.getThrowable() instanceof ProcessCanceledException ? null : result.getThrowable());
          if (indicatorDisposable != null) {
            Disposer.dispose(indicatorDisposable);
          }
        });
      }));
  }

  void notifyTaskFinished(@NotNull Task.Backgroundable task, long elapsed) {

  }

  // ASSERT IS EDT->UI bg or calling if cant
  // NEW: no assert; bg or calling ...
  protected boolean runProcessWithProgressSynchronously(@NotNull Task task) {
    Ref<Throwable> exceptionRef = new Ref<>();
    Runnable taskContainer = () -> {
      try {
        startTask(task, getProgressIndicator(), null);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        exceptionRef.set(e);
      }
    };

    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    boolean result = application.runProcessWithProgressSynchronously(taskContainer,
                                                                     task.getTitle(),
                                                                     task.isCancellable(),
                                                                     task.isModal(),
                                                                     task.getProject(),
                                                                     task.getParentComponent(),
                                                                     task.getCancelText());

    ApplicationUtil.invokeAndWaitSomewhere(task.whereToRunCallbacks(),
                                           application.getDefaultModalityState(),
                                           () -> finishTask(task, !result, exceptionRef.get()));
    return result;
  }

  public void runProcessWithProgressInCurrentThread(@NotNull Task task,
                                                    @NotNull ProgressIndicator progressIndicator,
                                                    @NotNull ModalityState modalityState) {
    if (progressIndicator instanceof Disposable) {
      Disposer.register(ApplicationManager.getApplication(), (Disposable)progressIndicator);
    }

    boolean processCanceled = false;
    Throwable exception = null;
    try {
      runProcess(() -> startTask(task, progressIndicator, null), progressIndicator);
    }
    catch (ProcessCanceledException e) {
      processCanceled = true;
    }
    catch (Throwable e) {
      exception = e;
    }

    boolean finalCanceled = processCanceled || progressIndicator.isCanceled();
    Throwable finalException = exception;

    ApplicationUtil.invokeAndWaitSomewhere(task.whereToRunCallbacks(), modalityState, () -> finishTask(task, finalCanceled, finalException));
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

  // bg
  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return getCurrentIndicator(Thread.currentThread());
  }

  // run in current thread
  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    computeUnderProgress(() -> {
      process.run();
      return null;
    }, progress);
  }

  @Override
  public boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action, @Nullable ProgressIndicator indicator) {
    ApplicationManager.getApplication().runReadAction(action);
    return true;
  }

  private <V, E extends Throwable> V computeUnderProgress(@NotNull ThrowableComputable<V, E> process, ProgressIndicator progress) throws E {
    if (progress == null) {
      myUnsafeProgressCount.incrementAndGet();
      try {
        return process.compute();
      }
      finally {
        myUnsafeProgressCount.decrementAndGet();
      }
    }

    ProgressIndicator oldIndicator = getProgressIndicator();
    if (progress == oldIndicator) {
      return process.compute();
    }

    Thread currentThread = Thread.currentThread();
    long threadId = currentThread.getId();
    setCurrentIndicator(threadId, progress);
    try {
      return registerIndicatorAndRun(progress, currentThread, oldIndicator, process);
    }
    finally {
      setCurrentIndicator(threadId, oldIndicator);
    }
  }

  // this thread
  private <V, E extends Throwable> V registerIndicatorAndRun(@NotNull ProgressIndicator indicator,
                                                             @NotNull Thread currentThread,
                                                             ProgressIndicator oldIndicator,
                                                             @NotNull ThrowableComputable<V, E> process) throws E {
    List<Set<Thread>> threadsUnderThisIndicator = new ArrayList<>();
    synchronized (threadsUnderIndicator) {
      boolean oneOfTheIndicatorsIsCanceled = false;

      for (ProgressIndicator thisIndicator = indicator;
           thisIndicator != null;
           thisIndicator = thisIndicator instanceof WrappedProgressIndicator
                           ? ((WrappedProgressIndicator)thisIndicator).getOriginalProgressIndicator()
                           : null) {
        Set<Thread> underIndicator = threadsUnderIndicator.computeIfAbsent(thisIndicator, __ -> new HashSet<>());
        boolean alreadyUnder = !underIndicator.add(currentThread);
        threadsUnderThisIndicator.add(alreadyUnder ? null : underIndicator);

        boolean isStandard = thisIndicator instanceof StandardProgressIndicator;
        if (!isStandard) {
          nonStandardIndicators.compute(thisIndicator, (__, count) -> {
            if (count == null) {
              return new AtomicInteger(1);
            }
            count.incrementAndGet();
            return count;
          });
          startBackgroundNonStandardIndicatorsPing();
        }

        oneOfTheIndicatorsIsCanceled |= thisIndicator.isCanceled();
      }

      updateThreadUnderCanceledIndicator(currentThread, oneOfTheIndicatorsIsCanceled);
    }

    try {
      return process.compute();
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
            AtomicInteger newCount = nonStandardIndicators.compute(thisIndicator, (__, count) -> {
              if (count.decrementAndGet() == 0) {
                return null;
              }
              return count;
            });
            if (newCount == null) {
              stopBackgroundNonStandardIndicatorsPing();
            }
          }
          // by this time oldIndicator may have been canceled
        }
        updateThreadUnderCanceledIndicator(currentThread, oldIndicator != null && oldIndicator.isCanceled());
      }
    }
  }

  private void updateThreadUnderCanceledIndicator(@NotNull Thread thread, boolean underCanceledIndicator) {
    boolean changed = underCanceledIndicator ? threadsUnderCanceledIndicator.add(thread) : threadsUnderCanceledIndicator.remove(thread);
    if (changed) {
      updateShouldCheckCanceled();
    }
  }

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
  private static final Thread[] EMPTY_THREAD_ARRAY = new Thread[0];
  private final Set<Thread> myPrioritizedThreads = ContainerUtil.newConcurrentSet();
  private volatile Thread[] myEffectivePrioritizedThreads = EMPTY_THREAD_ARRAY;
  private int myDeprioritizations; //guarded by myPrioritizationLock
  private final Object myPrioritizationLock = ObjectUtils.sentinel("myPrioritizationLock");
  private volatile long myPrioritizingStarted;

  @Override
  public <T, E extends Throwable> T computePrioritized(@NotNull ThrowableComputable<T, E> computable) throws E {
    Thread thread = Thread.currentThread();
    boolean prioritize;
    synchronized (myPrioritizationLock) {
      if (isCurrentThreadPrioritized()) {
        prioritize = false;
      }
      else {
        prioritize = true;
        if (myPrioritizedThreads.isEmpty()) {
          myPrioritizingStarted = System.nanoTime();
        }
        myPrioritizedThreads.add(thread);
        updateEffectivePrioritized();
      }
    }
    try {
      return computable.compute();
    }
    finally {
      if (prioritize) {
        synchronized (myPrioritizationLock) {
          myPrioritizedThreads.remove(thread);
          updateEffectivePrioritized();
        }
      }
    }
  }

  private void updateEffectivePrioritized() {
    Thread[] prev = myEffectivePrioritizedThreads;
    Thread[] current = myDeprioritizations > 0 || myPrioritizedThreads.isEmpty() ? EMPTY_THREAD_ARRAY
                                                                                 : myPrioritizedThreads.toArray(EMPTY_THREAD_ARRAY);
    myEffectivePrioritizedThreads = current;
    if (prev.length == 0 && current.length > 0) {
      prioritizingStarted();
    }
    else if (prev.length > 0 && current.length == 0) {
      prioritizingFinished();
    }
  }

  protected void prioritizingStarted() {}

  protected void prioritizingFinished() {}

  @ApiStatus.Internal
  public boolean isCurrentThreadPrioritized() {
    return myPrioritizedThreads.contains(Thread.currentThread());
  }

  @ApiStatus.Internal
  public void suppressPrioritizing() {
    synchronized (myPrioritizationLock) {
      if (++myDeprioritizations == 100 + ForkJoinPool.getCommonPoolParallelism() * 2) {
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
      threadTopLevelIndicators.putIfAbsent(threadId, indicator);
    }
  }

  private static ProgressIndicator getCurrentIndicator(@NotNull Thread thread) {
    return currentIndicators.get(thread.getId());
  }

  @FunctionalInterface
  interface CheckCanceledHook {
    /**
     * @param indicator the indicator whose {@link ProgressIndicator#checkCanceled()} was called,
     *                  or null if {@link ProgressManager#checkCanceled()} was called (even on a thread with indicator)
     * @return true if the hook has done anything that might take some time.
     */
    boolean runHook(@Nullable ProgressIndicator indicator);
  }

  public static void assertUnderProgress(@NotNull ProgressIndicator indicator) {
    synchronized (threadsUnderIndicator) {
      Set<Thread> threads = threadsUnderIndicator.get(indicator);
      if (threads == null || !threads.contains(Thread.currentThread())) {
        LOG.error("Must be executed under progress indicator: " + indicator + ". Please see e.g. ProgressManager.runProcess()");
      }
    }
  }
}
