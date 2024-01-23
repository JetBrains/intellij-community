// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class JobLauncherImpl extends JobLauncher {
  static final int CORES_FORK_THRESHOLD = 1;
  private static final Logger LOG = Logger.getInstance(JobLauncher.class);
  private final boolean logAllExceptions = System.getProperty("idea.job.launcher.log.all.exceptions", "false").equals("true");
  static final boolean joinWithoutTimeout = Boolean.getBoolean("idea.job.launcher.without.timeout");

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    // supply our own indicator even if we haven't given one - to support cancellation
    // use StandardProgressIndicator by default to avoid assertion in SensitiveProgressWrapper() ctr later
    ProgressIndicator wrapper = progress == null ? new StandardProgressIndicatorBase() : new SensitiveProgressWrapper(progress);

    Boolean result = processImmediatelyIfTooFew(things, wrapper, runInReadAction, thingProcessor);
    if (result != null) return result;

    ProgressManager pm = ProgressManager.getInstance();
    Processor<? super T> processor = ((CoreProgressManager)pm).isCurrentThreadPrioritized()
                                     ? t -> pm.computePrioritized(() -> thingProcessor.process(t))
                                     : thingProcessor;
    processor = FileBasedIndex.getInstance().inheritCurrentDumbAccessType(processor);
    processor = ClientId.decorateProcessor(processor);

    List<ApplierCompleter<T>> failedSubTasks = Collections.synchronizedList(new ArrayList<>());
    ApplierCompleter<T> applier = new ApplierCompleter<>(null, runInReadAction, failFastOnAcquireReadAction, wrapper, things, processor, 0, things.size(), failedSubTasks, null);
    try {
      if (progress != null && isAlreadyUnder(progress)) {
        // there must be nested invokeConcurrentlies.
        // In this case, try to avoid placing tasks to the FJP queue because extra applier.get() or pool.invoke() can cause pool over-compensation with too many workers
        applier.compute();
      }
      else {
        ForkJoinPool.commonPool().execute(applier);
      }
      // call checkCanceled a bit more often than .invoke()
      while (!applier.isDone()) {
        ProgressManager.checkCanceled();
        try {
          if (joinWithoutTimeout) {
            applier.get();
          } else {
            // does automatic compensation against starvation (in ForkJoinPool.awaitJoin)
            applier.get(10, TimeUnit.MILLISECONDS);
          }
        }
        catch (TimeoutException ignored) {
        }
        catch (ExecutionException e) {
          throw e.getCause();
        }
      }
      if (applier.throwable != null) {
        throw applier.throwable;
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
      return false;
    }
    catch (RuntimeException | Error e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    return applier.completeTaskWhichFailToAcquireReadAction();
  }

  private static boolean isAlreadyUnder(@NotNull ProgressIndicator progress) {
    progress = ProgressWrapper.unwrapAll(progress);
    ProgressIndicator existing = ProgressIndicatorProvider.getGlobalProgressIndicator();
    while (existing != null) {
      if (existing == progress) return true;
      if (!(existing instanceof WrappedProgressIndicator)) return false;
      existing = ProgressWrapper.unwrap(existing);
    }
    return false;
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
        //noinspection ForLoopReplaceableByForEach
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
  public @NotNull Job<Void> submitToJobThread(@NotNull Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback) {
    VoidForkJoinTask task = new VoidForkJoinTask(action, onDoneCallback);
    task.submit();
    return task;
  }

  private static final class VoidForkJoinTask implements Job<Void> {
    private final Runnable myAction;
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

    private VoidForkJoinTask(@NotNull Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback) {
      myAction = action;
      myOnDoneCallback = onDoneCallback;
    }

    private void submit() {
      ForkJoinPool.commonPool().execute(myForkJoinTask);
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
        // wait while helping other tasks in the meantime, but not for too long
        // we are avoiding calling timed myForkJoinTask.get() because it's very expensive when timed out (bc of TimeoutException)
        ForkJoinPool.commonPool().awaitQuiescence(Math.min(toWait, 10), TimeUnit.MILLISECONDS);
      }
      if (myForkJoinTask.isDone()) {
        try {
          myForkJoinTask.get();
        }
        catch (CancellationException e) {
          // was canceled in the middle of execution
        }
        catch (ExecutionException e) {
          ExceptionUtil.rethrow(e.getCause());
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
    // if the tombstone was removed by this batch operation, return it back to the queue to give chance tasks to stop themselves
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
      tasks.add(ForkJoinPool.commonPool().submit(new MyProcessQueueTask(i, i < firstElements.size() ? firstElements.get(i) : null)));
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
      ExceptionUtil.rethrow(exception);
    }
    return result;
  }
  private static final Object TOMBSTONE = ObjectUtils.sentinel("TOMBSTONE");
  private static <T> T TOMBSTONE() {
    //noinspection unchecked
    return (T)TOMBSTONE;
  }

  public interface QueueController<T> {
    void enqueue(T element);
    void dropEverythingAndPanic();
    void finish();
  }

  /**
   * Method for producing some elements (to process via {@code thingProcessor}), and scheduling their processing along with ongoing calculations.
   * It has three parts:
   *  - Produce elements to process concurrently. To produce the next element, call {@link QueueController#enqueue} and then, after all elements have been generated, {@link QueueController#finish()}, to mark there are no more elements.
   *    These elements are put in the BlockingQueue, after that several FJP tasks are spawned to deque elements from that queue and process them.
   *  - Processor to process each element.
   *    You can also add more elements to the queue there by calling {@link QueueController} methods inside.
   *  - otherActions runnable to be run during the queue processing, concurrently.
   *    You can also add more elements to the queue there by calling {@link QueueController} methods inside.
   * Processing happens in the queue-head to the queue-tail order, in parallel, maintaining {@link JobSchedulerImpl#getJobPoolParallelism} parallelism,
   * so the elements in the queue-head have higher priority than the tail.
   * Stop when all elements are processed (to mark the queue "no more accepting new elements", call {@link QueueController#finish()}.
   * Guarantees all tasks are completed in the method end, or runtime exception is thrown, in which case some elements might be in-flight.
   * @return true if all processors returned true
   */
  @ApiStatus.Internal
  public <T> boolean procInOrderAsync(@NotNull ProgressIndicator progress,
                                      int maxQueueSize,
                                      @NotNull PairProcessor<? super T, ? super QueueController<? super T>> thingProcessor,
                                      @NotNull Consumer<? super QueueController<? super T>> otherActions) throws ProcessCanceledException {
    progress.checkCanceled(); // do not start up expensive threads if there's no need to
    // optimization: if we know the max number of elements in the queue, use the cheaper ABQ
    BlockingQueue<T> things = maxQueueSize < Integer.MAX_VALUE ?
                              new ArrayBlockingQueue<>(maxQueueSize + JobSchedulerImpl.getJobPoolParallelism())
                              : new LinkedBlockingQueue<>();
    // start (up to CPU cores) parallel tasks but no more than (queue size)
    int n = Math.max(1, Math.min(maxQueueSize, JobSchedulerImpl.getJobPoolParallelism() - 1));

    QueueController<? super T> addToQueue = new QueueController<T>() {
      @Override
      public void enqueue(T t) {
        boolean added = things.offer(t);
        if (!added) {
          dropEverythingAndPanic();
        }
      }

      @Override
      public void dropEverythingAndPanic() {
        // drop everything you are doing and stop
        if (things.peek() != TOMBSTONE()) {
          things.clear();
        }
        finish();
      }

      @Override
      public void finish() {
        // mark the queue end, for all workers to take
        for (int i = 0; i < n; i++) {
          if (!things.offer(TOMBSTONE())) {
            // if the queue is not able to accommodate all tombstones, then someone else has already put tombstones there, and it's safe to ignore the false result
            break;
          }
        }
      }


    };

    AtomicBoolean futureResult = new AtomicBoolean(true);
    AtomicReference<Callable<Boolean>> firstTask = new AtomicReference<>();
    CountedCompleter<Boolean> completer = new CountedCompleter<>() {
      @Override
      public void compute() {
        try {
          firstTask.get().call();
        }
        catch (Exception e) {
          completeExceptionally(e);
        }
      }

      @Override
      public Boolean getRawResult() {
        return futureResult.get();
      }
    };

    // spawn up to (JobSchedulerImpl.getJobPoolParallelism() - 1) tasks,
    // each one trying to dequeue as many elements off `things` as possible and handing them to `thingProcessor`, until `tombStone` is hit
    final class MyProcessQueueTask implements Callable<Boolean> {
      private final int mySeq;
      private final CoroutineContext myContext = ThreadContext.currentThreadContext();

      private MyProcessQueueTask(int seq) {
        mySeq = seq;
      }

      @Override
      public Boolean call() {
        boolean[] result = new boolean[1];
        try {
          ProgressManager.getInstance().executeProcessUnderProgress(() -> {
            try {
              while (true) {
                T element = things.take();

                if (element == TOMBSTONE()) {
                  result[0] = true;
                  break;
                }
                try (AccessToken ignored = ThreadContext.installThreadContext(myContext, true)) {
                  ProgressManager.checkCanceled();
                  if (!thingProcessor.process(element, addToQueue)) {
                    break;
                  }
                }
                catch (RuntimeException|Error e) {
                  if (logAllExceptions) {
                    LOG.info("Failed to process " + element + ". Add too failed query.", e);
                  }
                  throw e;
                }
              }
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }, progress);
          completer.tryComplete();
          return result[0];
        }
        catch (Throwable throwable) {
          completer.completeExceptionally(throwable);
          return false;
        }
        finally {
          if (!result[0]) {
            // in case of exception, we need to cancel all tasks as quick as possible, so if there is a task blocked in ".take()" we could unblock it by offering tombstone
            addToQueue.dropEverythingAndPanic();
            futureResult.set(false);
          }
        }
      }

      @Override
      public @NonNls String toString() {
        return super.toString() + " seq="+mySeq;
      }
    }
    completer.setPendingCount(n-1);
    for (int i = 1; i < n; i++) {
      ForkJoinPool.commonPool().submit(new MyProcessQueueTask(i));
    }
    firstTask.set(new MyProcessQueueTask(0));

    try {
      // execute other actions while we are processing enqueued elements
      otherActions.accept(addToQueue);
    }
    catch (Exception e) {
      // in case of exception in normal flow, terminate background tasks
      addToQueue.dropEverythingAndPanic();
      throw e;
    }
    finally {
      // do not call future.get() to avoid overcompensation
      completer.invoke();
    }
    return futureResult.get();
  }
}

