/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import jsr166e.ForkJoinPool;
import jsr166e.ForkJoinTask;
import jsr166e.ForkJoinWorkerThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public class JobLauncherImpl extends JobLauncher {
  private static final AtomicLong bits = new AtomicLong();
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

  private static <T> boolean invokeConcurrentlyForAll(@NotNull final List<T> things,
                                                      boolean runInReadAction,
                                                      @NotNull final Processor<? super T> thingProcessor,
                                                      @NotNull ProgressIndicator wrapper) throws ProcessCanceledException {
    ApplierCompleter applier = new ApplierCompleter(null, runInReadAction, wrapper, things, thingProcessor, 0, things.size(), null);
    try {
      pool.invoke(applier);
      if (applier.throwable != null) throw applier.throwable;
    }
    catch (ApplierCompleter.ComputationAbortedException e) {
      return false;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Error e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    assert applier.isDone();
    return applier.completeTaskWhichFailToAcquireReadAction();
  }

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T>things,
                                                     ProgressIndicator progress,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull final Processor<T> thingProcessor) throws ProcessCanceledException {
    return invokeConcurrentlyUnderProgress(things, progress, ApplicationManager.getApplication().isReadAccessAllowed(),
                                           failFastOnAcquireReadAction, thingProcessor);
  }

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull final List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull final Processor<T> thingProcessor) throws ProcessCanceledException {
    if (things.isEmpty()) return true;
    // supply our own indicator even if we haven't given one - to support cancellation
    final ProgressIndicator wrapper = progress == null ? new ProgressIndicatorBase() : new SensitiveProgressWrapper(progress);

    if (things.size() <= 1 || JobSchedulerImpl.CORES_COUNT <= CORES_FORK_THRESHOLD) {
      final AtomicBoolean result = new AtomicBoolean(true);
      ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
        @Override
        public void run() {
          //noinspection ForLoopReplaceableByForEach
          for (int i = 0; i < things.size(); i++) {
            T thing = things.get(i);
            if (!thingProcessor.process(thing)) {
              result.set(false);
              break;
            }
          }
        }
      }, wrapper);
      return result.get();
    }

    return invokeConcurrentlyForAll(things, runInReadAction, thingProcessor, wrapper);
  }

  // This implementation is not really async
  @NotNull
  @Override
  public <T> AsyncFutureResult<Boolean> invokeConcurrentlyUnderProgressAsync(@NotNull List<? extends T> things,
                                                                             ProgressIndicator progress,
                                                                             boolean failFastOnAcquireReadAction,
                                                                             @NotNull Processor<T> thingProcessor) {
    final AsyncFutureResult<Boolean> asyncFutureResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    try {
      final boolean result = invokeConcurrentlyUnderProgress(things, progress, failFastOnAcquireReadAction, thingProcessor);
      asyncFutureResult.set(result);
    }
    catch (Throwable t) {
      asyncFutureResult.setException(t);
    }
    return asyncFutureResult;
  }

  @NotNull
  @Override
  public Job<Void> submitToJobThread(int priority, @NotNull final Runnable action, final Consumer<Future> onDoneCallback) {
    VoidForkJoinTask task = new VoidForkJoinTask(action, onDoneCallback);
    pool.submit(task);
    return task;
  }

  private static class VoidForkJoinTask extends ForkJoinTask<Void> implements Job<Void> {
    private final Runnable myAction;
    private final Consumer<Future> myOnDoneCallback;

    public VoidForkJoinTask(@NotNull Runnable action, @Nullable Consumer<Future> onDoneCallback) {
      myAction = action;
      myOnDoneCallback = onDoneCallback;
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
      try {
        myAction.run();
        complete(null); // complete manually before calling callback
      }
      catch (Throwable throwable) {
        completeExceptionally(throwable);
      }
      finally {
        if (myOnDoneCallback != null) {
          myOnDoneCallback.consume(this);
        }
      }
      return true;
    }

    //////////////// Job
    @Override
    public String getTitle() {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isCanceled() {
      return isCancelled();
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
      cancel(true);
    }

    @Override
    public void schedule() {
      throw new IncorrectOperationException();
    }

    @Override
    public void waitForCompletion(int millis) throws InterruptedException, ExecutionException, TimeoutException {
      get(millis, TimeUnit.MILLISECONDS);
    }
  }
}
