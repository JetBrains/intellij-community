// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

@ApiStatus.Internal
public final class JobLauncherImpl extends JobLauncher {
  private static final int PARALLELISM_THRESHOLD = 1;
  private static final Logger LOG = Logger.getInstance(JobLauncher.class);
  private final boolean logAllExceptions = System.getProperty("idea.job.launcher.log.all.exceptions", "false").equals("true");
  private final ForkJoinPool myForkJoinPool;
  private final ThreadFactory myThreadFactory;
  private final int myParallelism;

  JobLauncherImpl() {
    this(ForkJoinPool.commonPool(), JobSchedulerImpl.getJobPoolParallelism());
  }

  @VisibleForTesting
  JobLauncherImpl(@NotNull ForkJoinPool pool, int parallelism) {
    myForkJoinPool = pool;
    myParallelism = parallelism;
    myThreadFactory = IntelliJVirtualThreads.ofVirtualIn(pool)
      .name("JobLauncher-", 0)
      .inheritInheritableThreadLocals(false)
      .factory();
  }

  private static @NotNull Thread startThread(@NotNull ThreadFactory factory, @NotNull Runnable task) {
    Thread thread = factory.newThread(task);
    thread.start();
    return thread;
  }

  private static @NotNull ProgressIndicator assertUnderProgressIndicator() {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator == null) {
      throw new IllegalStateException("Must be run under ProgressIndicator. See ProgressManager.runProcess()");
    }
    return indicator;
  }

  //@RequiresBackgroundThread
  @Override
  @ApiStatus.Internal
  public <T> boolean processConcurrentlyAsync(@NotNull List<? extends T> things,
                                              @NotNull Processor<? super T> thingProcessor,
                                              @NotNull Runnable runWhileProcessing) throws ProcessCanceledException {
    ProgressIndicator progress = assertUnderProgressIndicator();
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();

    Boolean result = processImmediatelyIfTooFew(things, app.isWriteAccessAllowed(), myParallelism, thingProcessor, runWhileProcessing);
    if (result != null) {
      return result;
    }

    ProgressManager pm = ProgressManager.getInstance();
    Processor<? super T> processor = ((CoreProgressManager)pm).isCurrentThreadPrioritized()
                                     ? t -> pm.computePrioritized(() -> thingProcessor.process(t))
                                     : thingProcessor;
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstanceIfDefined();
    if (fileBasedIndex != null) {
      processor = fileBasedIndex.inheritCurrentDumbAccessType(processor);
    }

    List<ApplierCompleter<T>> failedSubTasks = Collections.synchronizedList(new ArrayList<>());

    // Start several tasks at once to reduce ramp-up.
    int chunk = Math.max(1, things.size() / myParallelism);
    //noinspection unchecked
    ApplierCompleter<T>[] globalCompleters = new ApplierCompleter[things.size() / chunk];
    int hi = things.size();
    boolean[] processed = new boolean[things.size()];
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    boolean runInReadAction = app.isReadAccessAllowed();
    boolean failFastOnAcquireReadAction = app.isInImpatientReader();
    ProgressIndicator wrapper = new SensitiveProgressWrapper(progress);
    for (int n=globalCompleters.length-1; n>=0; n--) {
      int lo = n == 0 ? 0 : hi - chunk;
      ApplierCompleter<T> completer =
        new ApplierCompleter<>(globalCompleters, n, thrown, runInReadAction, failFastOnAcquireReadAction, wrapper, things, processed, lo, hi, failedSubTasks, processor);
      globalCompleters[n] = completer;
      hi -= chunk;
    }

    // start only after all are initialized, because each is passed the globalCompleters array which must be inited
    for (ApplierCompleter<T> completer : globalCompleters) {
      startThread(myThreadFactory, completer);
    }
    try {
      runWhileProcessing.run();

      // help all others
      try (var _ = ThreadContext.resetThreadContext()) {
        safeIterate(globalCompleters, thrown, completer -> {
          wrapper.checkCanceled();
          completer.wrapAndRun(() -> completer.execAll());
        });

        // Wait for all processors and propagate their exceptions.
        RecursionManager.runInNewContext(() -> safeIterate(globalCompleters, thrown, completer -> {
          while (!completer.isDone()) {
            // Check cancellation unless a processor returned false and canceled the wrapper.
            if (wrapper.isCanceled() && !(thrown.get() instanceof ApplierCompleter.ComputationAbortedException)) {
              wrapper.checkCanceled();
            }
            if (!wrapper.isCanceled()) {
              try {
                completer.helpOtherCompleters();
              }
              catch (ApplierCompleter.ComputationAbortedException | ProcessCanceledException e) {
                if (!(thrown.get() instanceof ApplierCompleter.ComputationAbortedException)) {
                  throw e;
                }
              }
            }
            if (!completer.isDone()) {
              helpForkJoinPool(myForkJoinPool);
              if (!completer.isDone()) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
              }
            }
          }
          completer.get();
        }));
      }

      if (thrown.get() != null) {
        throw thrown.get();
      }
    }
    catch (ApplierCompleter.ComputationAbortedException e) {
      // one of the processors returned false
      return false;
    }
    catch (ApplicationUtil.CannotRunReadActionException e) {
      // failFastOnAcquireReadAction==true and one of the processors called runReadAction() during the pending write action
      throw e;
    }
    catch (ProcessCanceledException _) {
      // We should distinguish between genuine 'progress' cancellation and optimization when
      // task1.processor returns false and the task cancels the indicator then task2 calls checkCancel() and get here.
      // The former requires to re-throw PCE, the latter should just return false.
      progress.checkCanceled();
      ProgressManager.checkCanceled();
      Throwable savedException = thrown.get();
      if (savedException != null) {
        ApplierCompleter.rethrowUncheckedRaw(savedException);
      }
      return false;
    }
    catch (RuntimeException | Error e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    return ApplierCompleter.completeTaskWhichFailToAcquireReadAction(failedSubTasks);
  }

  private static <T> void safeIterate(ApplierCompleter<T> @NotNull [] globalCompleters,
                                      @NotNull AtomicReference<Throwable> thrown,
                                      @NotNull ThrowableConsumer<? super ApplierCompleter<T>, Throwable> consumer)
    throws ProcessCanceledException {
    for (ApplierCompleter<T> completer : globalCompleters) {
      try {
        consumer.consume(completer);
      }
      catch (ProcessCanceledException e) {
        // when some ApplierCompleter caught ComputationAbortedException, it cancels the current indicator, thus making other ApplierCompleter throw PCE
        // we need to catch this induced PCE here and return false instead
        if (!(thrown.get() instanceof ApplierCompleter.ComputationAbortedException)) {
          throw e;
        }
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ApplierCompleter.ComputationAbortedException) {
          // contract: when some processor returned false (but not when PCE happened), wait until all other tasks are terminated before return from invokeConcurrentlyUnderProgress
          thrown.set(cause);
          continue;
        }
        cause = ApplierCompleter.accumulateException(thrown, cause);
        ApplierCompleter.rethrowUncheckedRaw(cause);
      }
      catch (ApplierCompleter.ComputationAbortedException e) {
        // contract: when some processor returned false (but not when PCE happened), wait until all other tasks are terminated before return from invokeConcurrentlyUnderProgress
        thrown.set(e);
      }
      catch (Throwable e) {
        ApplierCompleter.rethrowUncheckedRaw(ApplierCompleter.accumulateException(thrown, e));
      }
    }
  }
  // If {@code things} are too few for concurrent processing, this method returns the processing result.
  // It returns null when the things require concurrent processing.
  private static <T> Boolean processImmediatelyIfTooFew(@NotNull List<? extends T> things,
                                                        boolean isWriteAccessAllowed,
                                                        int parallelism,
                                                        @NotNull Processor<? super T> thingProcessor,
                                                        @NotNull Runnable runWhileProcessing) {
    if (things.size() <= 1 ||
        parallelism <= PARALLELISM_THRESHOLD ||
        isWriteAccessAllowed) {
      runWhileProcessing.run();
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < things.size(); i++) {
        T thing = things.get(i);
        if (!thingProcessor.process(thing)) {
          return false;
        }
      }
      return true;
    }
    return null;
  }

  public @NotNull Job submitToJobThread(@NotNull Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback) {
    return new VirtualThreadJob(action, myThreadFactory, myForkJoinPool, onDoneCallback);
  }

  private static final class VirtualThreadJob implements Job, Runnable {
    private final Runnable myAction;
    private final Consumer<? super Future<?>> myOnDoneCallback;
    private final JobFuture myFuture = new JobFuture();
    private final AtomicReference<Status> myStatus = new AtomicReference<>(Status.NEW);
    private final Thread myThread;
    private final ForkJoinPool myForkJoinPool;

    private enum Status { NEW, STARTED, DONE }

    private VirtualThreadJob(@NotNull Runnable action,
                             @NotNull ThreadFactory threadFactory,
                             @NotNull ForkJoinPool pool,
                             @Nullable Consumer<? super Future<?>> onDoneCallback) {
      myAction = action;
      myOnDoneCallback = onDoneCallback;
      myForkJoinPool = pool;
      myThread = startThread(threadFactory, this);
    }

    @Override
    public void run() {
      if (!myStatus.compareAndSet(Status.NEW, Status.STARTED)) {
        return;
      }
      try {
        myAction.run();
        myFuture.complete();
      }
      catch (Throwable e) {
        myFuture.completeExceptionally(e);
      }
      finally {
        myStatus.set(Status.DONE);
        if (myOnDoneCallback != null) {
          new FutureTask<>(() -> {
            myOnDoneCallback.accept(myFuture);
            return null;
          }).run();
        }
      }
    }

    // when canceled in the middle of the execution returns false until finished
    @Override
    public boolean isDone() {
      return myStatus.get() == Status.DONE;
    }

    @Override
    public boolean isCanceled() {
      return myFuture.isCancelled();
    }

    @Override
    public void cancel() {
      myStatus.compareAndSet(Status.NEW, Status.DONE);
      myFuture.cancel(false);
    }

    // waits for the job to finish execution (when called on a canceled job in the middle of the execution, wait for finish)
    @Override
    public boolean waitForCompletion(int millis) throws InterruptedException {
      if (millis <= 0) {
        return isDone();
      }
      long deadline = System.currentTimeMillis() + millis;
      while (!isDone()) {
        long toWait = deadline - System.currentTimeMillis();
        if (toWait <= 0) {
          return isDone();
        }
        joinThread(myForkJoinPool, myThread, Math.min(toWait, 10));
      }
      if (myFuture.isDone()) {
        try {
          myFuture.get();
        }
        catch (CancellationException e) {
          // was canceled in the middle of the execution
        }
        catch (ExecutionException e) {
          ApplierCompleter.rethrowUncheckedRaw(ObjectUtils.notNull(e.getCause(), e));
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return "VirtualThreadJob: status:" + myStatus + "; future:" + myFuture;
    }

    private static final class JobFuture extends FutureTask<Void> {
      private JobFuture() {
        super(() -> null);
      }

      private void complete() {
        set(null);
      }

      private void completeExceptionally(@NotNull Throwable e) {
        setException(e);
      }
    }
  }

  /// Processes `failedToProcess` and then `things` in virtual threads and the current thread under the `progress`
  /// The processing starts at the queue head and maintains [JobSchedulerImpl#getJobPoolParallelism] parallelism.
  /// Thus, the elements at the queue head have a higher priority.
  /// The processing stops at `tombStone`.
  /// If `thingProcessor` throws an exception, this method adds the current element to `failedToProcess`.
  ///
  /// @return true if all elements are processed successfully; false if a processor returns false or throws an exception
  @ApiStatus.Internal
  public <T> boolean processQueue(@NotNull BlockingQueue<@NotNull T> things,
                                  @NotNull Queue<@NotNull T> failedToProcess,
                                  @NotNull ProgressIndicator progress,
                                  @NotNull T tombStone,
                                  @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    // Start up to (JobSchedulerImpl.getJobPoolParallelism() - 1) tasks.
    // Each task processes elements from `things` until it finds `tombStone`.
    final class MyProcessQueueTask implements Callable<Boolean> {
      private final int mySeq;
      private final T myFirstTask;

      private final CoroutineContext myContext = ThreadContext.currentThreadContext();

      private MyProcessQueueTask(int seq, @Nullable T firstTask) {
        mySeq = seq;
        myFirstTask = firstTask;
      }

      @Override
      public Boolean call() {
        boolean[] result = new boolean[1];
        return ThreadContext.installThreadContext(myContext, true, () -> {
        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          T element = myFirstTask;
          try {
            while (true) {
              if (element == null) {
                progress.checkCanceled();
                element = failedToProcess.poll();
                if (element == null) {
                  element = things.poll(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                continue;
              }
              if (element == tombStone) {
                things.put(tombStone); // return just popped tombStone to the 'things' queue for everybody else to see it
                // since the queue is drained up to the tombStone, there surely should be a place for one element, so "put" will not block
                result[0] = true;
                break;
              }
              progress.checkCanceled();
              if (!thingProcessor.process(element)) {
                break;
              }
              element = null;
            }
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          catch (RuntimeException|Error e) {
            if (element != null) {
              failedToProcess.add(element);
            }
            if (logAllExceptions) {
              rethrowControlFlowException(e);
              LOG.info("Failed to process " + element + ". Add too failed query.", e);
            }
            throw e;
          }
        }, progress);
        return result[0];
      });
    }

      @Override
      public @NonNls String toString() {
        return super.toString() + " seq="+mySeq;
      }
    }
    progress.checkCanceled(); // do not start up expensive threads if there's no need to
    boolean isQueueBounded = things.contains(tombStone);
    int remainingSize = things.size() + failedToProcess.size() - (isQueueBounded ? 1 : 0);
    // start up (CPU cores) parallel tasks but no more than (queue size)
    int n = Math.clamp(isQueueBounded ? remainingSize : Integer.MAX_VALUE, 1, Math.max(1, myParallelism - 1));
    record QueueTask(FutureTask<Boolean> task, Thread thread) { }
    List<QueueTask> tasks = new ArrayList<>(n-1);
    List<T> firstElements = new ArrayList<>(n);
    things.drainTo(firstElements, n);
    // if the tombstone was removed by this batch operation, return it to the queue to give a chance to other tasks to stop themselves
    if (ContainerUtil.getLastItem(firstElements) == tombStone) {
      firstElements.removeLast();
      try {
        things.put(tombStone);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }
    for (int i = 1; i < n; i++) {
      FutureTask<Boolean> task = new FutureTask<>(new MyProcessQueueTask(i, i < firstElements.size() ? firstElements.get(i) : null));
      tasks.add(new QueueTask(task, startThread(myThreadFactory, task)));
    }
    MyProcessQueueTask firstTask = new MyProcessQueueTask(0, ContainerUtil.getFirstItem(firstElements));
    // execute the first task directly in this thread to avoid thread starvation
    boolean result = false;
    Throwable exception = null;
    try {
      result = firstTask.call();
    }
    catch (Throwable e) {
      exception = e;
    }
    for (QueueTask task : tasks) {
      try {
        joinUninterruptibly(myForkJoinPool, task.thread());
        //noinspection NonShortCircuitBooleanExpression
        result &= task.task().get();
      }
      catch (ExecutionException e) {
        exception = e.getCause();
      }
      catch (Throwable e) {
        exception = e;
      }
    }
    if (exception != null) {
      ApplierCompleter.rethrowUncheckedRaw(exception);
    }
    return result;
  }

  private static void helpForkJoinPool(@NotNull ForkJoinPool pool) {
    if (Thread.currentThread().isVirtual()) {
      return;
    }
    boolean interrupted = Thread.interrupted();
    try {
      var _ = pool.awaitQuiescence(1, TimeUnit.MILLISECONDS);
    }
    finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void joinThread(@NotNull ForkJoinPool pool, @NotNull Thread thread, long millis) throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    if (!Thread.currentThread().isVirtual()) {
      helpForkJoinPool(pool);
      millis = millis == 0 ? 1 : Math.min(millis, 1);
    }
    thread.join(millis);
  }

  private static void joinUninterruptibly(@NotNull ForkJoinPool pool, @NotNull Thread thread) {
    boolean interrupted = false;
    while (thread.isAlive()) {
      try {
        joinThread(pool, thread, 0);
      }
      catch (InterruptedException _) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
