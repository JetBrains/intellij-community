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
package com.intellij.concurrency;

import com.intellij.Patches;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import jsr166e.ForkJoinPool;
import jsr166e.ForkJoinTask;
import jsr166e.ForkJoinWorkerThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public class JobLauncherImpl extends JobLauncher {
  private static final AtomicLong bits = new AtomicLong();
  static {
    assert Patches.USE_REFLECTION_TO_ACCESS_JDK8 : "Please port to java.util.concurrent.ForkJoinPool";
  }
  private static final ForkJoinPool.ForkJoinWorkerThreadFactory FACTORY = new ForkJoinPool.ForkJoinWorkerThreadFactory() {
    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
      final int n = addThread();
      ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
        @Override
        protected void onTermination(Throwable exception) {
          finishThread(n);
          super.onTermination(exception);
        }
      };
      thread.setName("JobScheduler FJ pool "+ n +"/"+ JobSchedulerImpl.CORES_COUNT);
      return thread;
    }

    private int addThread() {
      boolean set;
      int n;
      do {
        long l = bits.longValue();
        long next = (l + 1) | l;
        n = Long.numberOfTrailingZeros(l + 1);
        set = bits.compareAndSet(l, next);
      } while (!set);
      return n;
    }
    private void finishThread(int n) {
      boolean set;
      do {
        long l = bits.get();
        long next = l & ~(1L << n);
        set = bits.compareAndSet(l, next);
      } while (!set);
    }
  };

  private static final ForkJoinPool pool = new ForkJoinPool(JobSchedulerImpl.CORES_COUNT, FACTORY, null, false);
  static final int CORES_FORK_THRESHOLD = 1;

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull final List<T> things,
                                                     ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull final Processor<? super T> thingProcessor) throws ProcessCanceledException {
    // supply our own indicator even if we haven't given one - to support cancellation
    final ProgressIndicator wrapper = progress == null ? new AbstractProgressIndicatorBase() : new SensitiveProgressWrapper(progress);

    Boolean result = processImmediatelyIfTooFew(things, wrapper, runInReadAction, thingProcessor);
    if (result != null) return result.booleanValue();

    ApplierCompleter<T> applier = new ApplierCompleter<>(null, runInReadAction, wrapper, things, thingProcessor, 0, things.size(), null);
    try {
      pool.invoke(applier);
      if (applier.throwable != null) throw applier.throwable;
    }
    catch (ApplierCompleter.ComputationAbortedException e) {
      return false;
    }
    catch (ProcessCanceledException e) {
      // task1.processor returns false and the task cancels the indicator
      // then task2 calls checkCancel() and get here
      return false;
    }
    catch (RuntimeException | Error e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    assert applier.isDone();
    return applier.completeTaskWhichFailToAcquireReadAction();
  }

  // if {@code things} are too few to be processed in the real pool, returns TRUE if processed successfully, FALSE if not
  // returns null if things need to be processed in the real pool
  private static <T> Boolean processImmediatelyIfTooFew(@NotNull final List<T> things,
                                                        @NotNull final ProgressIndicator progress,
                                                        boolean runInReadAction,
                                                        @NotNull final Processor<? super T> thingProcessor) {
    // commit can be invoked from within write action
    //if (runInReadAction && ApplicationManager.getApplication().isWriteAccessAllowed()) {
    //  throw new RuntimeException("Must not run invokeConcurrentlyUnderProgress() from under write action because of imminent deadlock");
    //}
    if (things.isEmpty()) return true;

    if (things.size() <= 1 || JobSchedulerImpl.CORES_COUNT <= CORES_FORK_THRESHOLD) {
      final AtomicBoolean result = new AtomicBoolean(true);
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
        if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(runnable)) return false;
      }
      else {
        runnable.run();
      }
      return result.get();
    }
    return null;
  }

  // This implementation is not really async

  @NotNull
  @Override
  public <T> AsyncFuture<Boolean> invokeConcurrentlyUnderProgressAsync(@NotNull List<T> things,
                                                                       ProgressIndicator progress,
                                                                       boolean failFastOnAcquireReadAction,
                                                                       @NotNull Processor<? super T> thingProcessor) {
    return AsyncUtil.wrapBoolean(invokeConcurrentlyUnderProgress(things, progress, failFastOnAcquireReadAction, thingProcessor));
  }

  @NotNull
  @Override
  public Job<Void> submitToJobThread(@NotNull final Runnable action, @Nullable Consumer<Future> onDoneCallback) {
    VoidForkJoinTask task = new VoidForkJoinTask(action, onDoneCallback);
    task.submit();
    return task;
  }

  private static class VoidForkJoinTask implements Job<Void> {
    private final Runnable myAction;
    private final Consumer<Future> myOnDoneCallback;
    private enum Status { STARTED, EXECUTED } // null=not yet executed, STARTED=started execution, EXECUTED=finished
    private volatile Status myStatus;
    private final ForkJoinTask<Void> myForkJoinTask = new ForkJoinTask<Void>() {
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
          completeExceptionally(throwable);
        }
        finally {
          myStatus = Status.EXECUTED;
          if (myOnDoneCallback != null) {
            myOnDoneCallback.consume(this);
          }
        }
        return true;
      }
    };

    private VoidForkJoinTask(@NotNull Runnable action, @Nullable Consumer<Future> onDoneCallback) {
      myAction = action;
      myOnDoneCallback = onDoneCallback;
    }

    private void submit() {
      pool.submit(myForkJoinTask);
    }
    //////////////// Job

    // when canceled in the middle of the execution returns false until finished
    @Override
    public boolean isDone() {
      boolean wasCancelled = myForkJoinTask.isCancelled(); // must be before status check
      Status status = myStatus;
      return status == Status.EXECUTED || status == null && wasCancelled;
    }

    @Override
    public String getTitle() {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isCanceled() {
      return myForkJoinTask.isCancelled();
    }

    @Override
    public void addTask(@NotNull Callable<Void> task) {
      throw new IncorrectOperationException();
    }

    @Override
    public void addTask(@NotNull Runnable task, Void result) {
      throw new IncorrectOperationException();
    }

    @Override
    public void addTask(@NotNull Runnable task) {
      throw new IncorrectOperationException();
    }

    @Override
    public List<Void> scheduleAndWaitForResults() throws Throwable {
      throw new IncorrectOperationException();
    }

    @Override
    public void cancel() {
      myForkJoinTask.cancel(true);
    }

    @Override
    public void schedule() {
      throw new IncorrectOperationException();
    }

    // waits for the job to finish execution (when called on a canceled job in the middle of the execution, wait for finish)
    @Override
    public void waitForCompletion(int millis) throws InterruptedException, ExecutionException, TimeoutException {
      while (!isDone()) {
        try {
          myForkJoinTask.get(millis, TimeUnit.MILLISECONDS);
          break;
        }
        catch (CancellationException e) {
          // was canceled in the middle of execution
          // can't do anything but wait. help other tasks in the meantime
          if (Thread.currentThread() instanceof ForkJoinWorkerThread) { // if called outside FJP the FJTask.fork() starts up commonPool which is undesirable
            pool.awaitQuiescence(millis, TimeUnit.MILLISECONDS);
          }
        }
      }
    }
  }

  /**
   * Process all elements from the {@code failedToProcess} and then {@code things} concurrently in the underlying pool.
   * Processing happens concurrently maintaining {@code JobSchedulerImpl.CORES_COUNT} parallelism.
   * Stop when {@code tombStone} element is occurred.
   * If was unable to process some element, add it back to the {@code failedToProcess} queue.
   * @return true if all elements processed successfully, false if at least one processor returned false or exception occurred
   */
  public <T> boolean processQueue(@NotNull final BlockingQueue<T> things,
                                  @NotNull final Queue<T> failedToProcess,
                                  @NotNull final ProgressIndicator progress,
                                  @NotNull final T tombStone,
                                  @NotNull final Processor<? super T> thingProcessor) {
    class MyTask implements Callable<Boolean> {
      private final int mySeq;
      private boolean result;

      private MyTask(int seq) {
        mySeq = seq;
      }

      @Override
      public Boolean call() throws Exception {
        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          try {
            while (true) {
              progress.checkCanceled();
              T element = failedToProcess.poll();
              if (element == null) element = things.take();

              if (element == tombStone) {
                things.offer(element);
                result = true;
                break;
              }
              try {
                if (!thingProcessor.process(element)) {
                  result = false;
                  break;
                }
              }
              catch (RuntimeException e) {
                failedToProcess.add(element);
                throw e;
              }
            }
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }, progress);
        return result;
      }

      @Override
      public String toString() {
        return super.toString() + " seq="+mySeq;
      }
    }

    boolean isSmallEnough = things.contains(tombStone);
    if (isSmallEnough) {
      try {
        // do not distribute for small queues
        return new MyTask(0).call();
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    List<ForkJoinTask<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < JobSchedulerImpl.CORES_COUNT; i++) {
      tasks.add(pool.submit(new MyTask(i)));
    }

    boolean result = true;
    RuntimeException exception = null;
    for (ForkJoinTask<Boolean> task : tasks) {
      try {
        result &= task.join();
      }
      catch (RuntimeException e) {
        exception = e;
      }
    }
    if (exception != null) {
      throw exception;
    }
    return result;
  }
}
