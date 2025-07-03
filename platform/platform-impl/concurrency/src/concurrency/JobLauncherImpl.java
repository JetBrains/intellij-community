// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class JobLauncherImpl extends JobLauncher {
  @ApiStatus.Internal
  public static final int CORES_FORK_THRESHOLD = 1;
  private static final Logger LOG = Logger.getInstance(JobLauncher.class);
  private final boolean logAllExceptions = System.getProperty("idea.job.launcher.log.all.exceptions", "false").equals("true");
  private final ForkJoinPool myForkJoinPool;

  @VisibleForTesting
  public JobLauncherImpl(@NotNull ForkJoinPool pool) {
    myForkJoinPool = pool;
  }

  JobLauncherImpl() {
    this(ForkJoinPool.commonPool());
  }

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    return invokeConcurrentlyUnderProgressAsync(things, progress, runInReadAction, failFastOnAcquireReadAction, thingProcessor, ()->{});
  }

  private static <T> boolean invokeConcurrentlyUnderProgressAsync(@NotNull List<? extends T> things,
                                                                  ProgressIndicator progress,
                                                                  boolean runInReadAction,
                                                                  boolean failFastOnAcquireReadAction,
                                                                  @NotNull Processor<? super T> thingProcessor,
                                                                  @NotNull Runnable runWhileForking) {
    // supply our own indicator even if we haven't given one - to support cancellation
    // use StandardProgressIndicator by default to avoid assertion in SensitiveProgressWrapper() ctr later
    ProgressIndicator wrapper = progress == null ? new StandardProgressIndicatorBase() : new SensitiveProgressWrapper(progress);

    Boolean result = processImmediatelyIfTooFew(things, wrapper, runInReadAction, thingProcessor);
    if (result != null) {
      runWhileForking.run();
      return result;
    }

    ProgressManager pm = ProgressManager.getInstance();
    Processor<? super T> processor = ((CoreProgressManager)pm).isCurrentThreadPrioritized()
                                     ? t -> pm.computePrioritized(() -> thingProcessor.process(t))
                                     : thingProcessor;
    processor = FileBasedIndex.getInstance().inheritCurrentDumbAccessType(processor);

    List<ApplierCompleter<T>> failedSubTasks = Collections.synchronizedList(new ArrayList<>());

    int availableParallelism = JobSchedulerImpl.getJobPoolParallelism();
    // fork off several subtasks at once to reduce ramp-up
    int chunk = Math.max(1, things.size() / availableParallelism);
    //noinspection unchecked
    ApplierCompleter<T>[] globalCompleters = new ApplierCompleter[things.size() / chunk];
    int hi = things.size();
    boolean[] processed = new boolean[things.size()];
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    for (int n=globalCompleters.length-1; n>=0; n--) {
      int lo = n == 0 ? 0 : hi - chunk;
      ApplierCompleter<T> completer =
        new ApplierCompleter<>(globalCompleters, n, thrown, runInReadAction, failFastOnAcquireReadAction, wrapper, things, processed, lo, hi, failedSubTasks, processor);
      globalCompleters[n] = completer;
      hi -= chunk;
    }

    // start only after all are initialised, because each is passed the globalCompleters array which must be inited
    for (ApplierCompleter<T> completer : globalCompleters) {
      completer.fork();
    }
    try {
      runWhileForking.run();

      // help all others
      ThreadContext.resetThreadContext(() -> {
        safeIterate(globalCompleters, thrown, completer -> {
          wrapper.checkCanceled();
          // don't call .invoke() or other FJP-setting status functions
          completer.wrapAndRun(() -> completer.execAll());
        });
        return null;
      });
      // all work is done or distributed; wait for in-flight appliers and manifest exceptions
      safeIterate(globalCompleters, thrown, completer -> {
        while (true) {
          try {
            // while waiting for completion, check for cancellation, except when some applier returned false and the wrapper was canceled because of that
            if (wrapper.isCanceled() && !(thrown.get() instanceof ApplierCompleter.ComputationAbortedException)) {
              wrapper.checkCanceled();
            }
            // optimization
            if (completer.isDone()) {
              completer.get();
            }
            else {
              Ref<Throwable> throwableRef = new Ref<>(null);
              ThreadContext.resetThreadContext(() -> {
                try {
                  completer.get(1, TimeUnit.MILLISECONDS);
                }
                catch (Throwable e) {
                  throwableRef.set(e);
                }
                return null;
              });
              Throwable throwable = throwableRef.get();
              if (throwable != null) {
                throw throwable;
              }
            }
            break;
          }
          catch (TimeoutException ignored) {
          }
        }
      });

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
    catch (ProcessCanceledException e) {
      // We should distinguish between genuine 'progress' cancellation and optimization when
      // task1.processor returns false and the task cancels the indicator then task2 calls checkCancel() and get here.
      // The former requires to re-throw PCE, the latter should just return false.
      if (progress != null) {
        progress.checkCanceled();
      }
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
        e = ApplierCompleter.accumulateException(thrown, e);
        ApplierCompleter.rethrowUncheckedRaw(e);
      }
    }
  }
  // if {@code things} are too few to be processed in the real pool, returns TRUE if processed successfully, FALSE if not
  // returns null if things need to be processed in the real pool
  private static <T> Boolean processImmediatelyIfTooFew(@NotNull List<? extends T> things,
                                                        @NotNull ProgressIndicator progress,
                                                        boolean runInReadAction,
                                                        @NotNull Processor<? super T> thingProcessor) {
    if (things.isEmpty()) return true;

    if (things.size() == 1 ||
        JobSchedulerImpl.getJobPoolParallelism() <= CORES_FORK_THRESHOLD ||
        runInReadAction && ApplicationManager.getApplication().isWriteAccessAllowed()
      ) {
      AtomicBoolean result = new AtomicBoolean(true);
      Runnable runnable = () -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        for (int i = 0; i < things.size(); i++) {
          T thing = things.get(i);
          if (!thingProcessor.process(thing)) {
            result.set(false);
            break;
          }
        }
      }, progress);
      if (runInReadAction) {
        ApplicationManager.getApplication().runReadAction(runnable);
      }
      else {
        runnable.run();
      }
      return result.get();
    }
    return null;
  }

  @Override
  public @NotNull Job submitToJobThread(@NotNull Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback) {
    VoidForkJoinTask task = new VoidForkJoinTask(action, myForkJoinPool, onDoneCallback);
    task.submit();
    return task;
  }

  private static final class VoidForkJoinTask implements Job {
    private final Runnable myAction;
    private final ForkJoinPool myForkJoinPool;
    private final Consumer<? super Future<?>> myOnDoneCallback;
    private enum Status { STARTED, EXECUTED } // null=not yet executed, STARTED=started execution, EXECUTED=finished
    private volatile Status myStatus;
    private final ForkJoinTask<Void> myForkJoinTask = new ForkJoinTask<>() {
      @Override
      public Void getRawResult() {
        return null;
      }

      @Override
      protected void setRawResult(Void value) {
      }

      @Override
      protected boolean exec() {
        myStatus = Status.STARTED;
        try {
          myAction.run();
          complete(null); // complete manually before calling callback
        }
        catch (Throwable throwable) {
          myStatus = Status.EXECUTED;
          completeExceptionally(throwable);
        }
        finally {
          myStatus = Status.EXECUTED;
          if (myOnDoneCallback != null) {
            myOnDoneCallback.accept(this);
          }
        }
        return true;
      }
    };

    private VoidForkJoinTask(@NotNull Runnable action, @NotNull ForkJoinPool forkJoinPool, @Nullable Consumer<? super Future<?>> onDoneCallback) {
      myAction = action;
      myForkJoinPool = forkJoinPool;
      myOnDoneCallback = onDoneCallback;
    }

    private void submit() {
      myForkJoinPool.execute(myForkJoinTask);
    }

    // when canceled in the middle of the execution returns false until finished
    @Override
    public boolean isDone() {
      boolean wasCancelled = myForkJoinTask.isCancelled(); // must be before status check
      Status status = myStatus;
      return status == Status.EXECUTED || status == null && wasCancelled;
    }

    @Override
    public boolean isCanceled() {
      return myForkJoinTask.isCancelled();
    }

    @Override
    public void cancel() {
      myForkJoinTask.cancel(true);
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
        if (toWait < 0) {
          return false;
        }
        ThreadContext.resetThreadContext(() -> {
          // wait while helping other tasks in the meantime, but not for too long
          // we are avoiding calling timed myForkJoinTask.get() because it's very expensive when timed out (bc of TimeoutException)
          myForkJoinPool.awaitQuiescence(Math.min(toWait, 10), TimeUnit.MILLISECONDS);
          return null;
        });
      }
      if (myForkJoinTask.isDone()) {
        try {
          myForkJoinTask.get();
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
  }

  /**
   * Process all elements from the {@code failedToProcess} and then {@code things} concurrently in the system's ForkJoinPool and the current thread.
   * Processing happens in the queue-head to the queue-tail order, but in parallel maintaining {@link JobSchedulerImpl#getJobPoolParallelism} parallelism,
   * so the elements in the queue-head have higher priority than the tail.
   * Stop when {@code tombStone} element is occurred.
   * If was unable to process some element (an exception occurred during {@code thingProcessor.process()} call), add it back to the {@code failedToProcess} queue.
   * @return true if all elements processed successfully, false if at least one processor returned false or exception occurred
   */
  @ApiStatus.Internal
  public <T> boolean processQueue(@NotNull BlockingQueue<@NotNull T> things,
                                  @NotNull Queue<@NotNull T> failedToProcess,
                                  @NotNull ProgressIndicator progress,
                                  @NotNull T tombStone,
                                  @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    // spawn up to (JobSchedulerImpl.getJobPoolParallelism() - 1) tasks,
    // each one trying to dequeue as many elements off `things` as possible and handing them to `thingProcessor`, until `tombStone` is hit
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
        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          try {
            T element = myFirstTask;
            while (true) {
              if (element == null) element = failedToProcess.poll();
              if (element == null) element = things.take();

              if (element == tombStone) {
                things.put(tombStone); // return just popped tombStone to the 'things' queue for everybody else to see it
                // since the queue is drained up to the tombStone, there surely should be a place for one element, so "put" will not block
                result[0] = true;
                break;
              }
              try (AccessToken ignored = ThreadContext.installThreadContext(myContext, true)) {
                ProgressManager.checkCanceled();
                if (!thingProcessor.process(element)) {
                  break;
                }
              }
              catch (RuntimeException|Error e) {
                if (logAllExceptions) {
                  LOG.info("Failed to process " + element + ". Add too failed query.", e);
                }
                failedToProcess.add(element);
                throw e;
              }
              element = null;
            }
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }, progress);
        return result[0];
      }

      @Override
      public @NonNls String toString() {
        return super.toString() + " seq="+mySeq;
      }
    }
    progress.checkCanceled(); // do not start up expensive threads if there's no need to
    int size = things.size();
    boolean isQueueBounded = things.contains(tombStone);
    // start up (CPU cores) parallel tasks but no more than (queue size)
    int n = Math.max(1, Math.min(isQueueBounded ? size-1 : Integer.MAX_VALUE, JobSchedulerImpl.getJobPoolParallelism() - 1));
    List<ForkJoinTask<Boolean>> tasks = new ArrayList<>(n-1);
    List<T> firstElements = new ArrayList<>(n);
    things.drainTo(firstElements, n);
    // if the tombstone was removed by this batch operation, return it to the queue to give a chance to other tasks to stop themselves
    if (ContainerUtil.getLastItem(firstElements) == tombStone) {
      firstElements.remove(firstElements.size() - 1);
      try {
        things.put(tombStone);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }
    for (int i = 1; i < n; i++) {
      tasks.add(myForkJoinPool.submit(new MyProcessQueueTask(i, i < firstElements.size() ? firstElements.get(i) : null)));
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
    for (ForkJoinTask<Boolean> task : tasks) {
      try {
        //noinspection NonShortCircuitBooleanExpression
        result &= task.join();
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

  @Override
  @ApiStatus.Internal
  public <T> boolean processConcurrentlyAsync(@NotNull ProgressIndicator progress,
                                              @NotNull List<? extends T> items,
                                              @NotNull Processor<? super T> thingProcessor,
                                              @NotNull Runnable runnable) throws ProcessCanceledException {
    return invokeConcurrentlyUnderProgressAsync(items, progress, true, true, thingProcessor, runnable);
  }
}

