package org.jetbrains.jps.api;

import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/2/12
 */
public class BoundedTaskExecutor implements Executor {
  protected final Executor myBackendExecutor;
  private final int myMaxTasks;
  private final AtomicInteger myInProgress = new AtomicInteger(0);
  private final Queue<FutureTask> myTaskQueue = new LinkedBlockingQueue<FutureTask>();

  private final Runnable USER_TASK_RUNNER = new Runnable() {
    public void run() {
      final FutureTask task = myTaskQueue.poll();
      try {
        if (task != null && !task.isCancelled()) {
          task.run();
        }
      }
      finally {
        myInProgress.decrementAndGet();
        if (!myTaskQueue.isEmpty()) {
          processQueue();
        }
      }
    }
  };

  public BoundedTaskExecutor(Executor backendExecutor, int maxSimultaneousTasks) {
    myBackendExecutor = backendExecutor;
    myMaxTasks = Math.max(maxSimultaneousTasks, 1);
  }

  @Override
  public void execute(Runnable task) {
    submit(task);
  }

  public Future submit(Runnable task) {
    final RunnableFuture<Void> future = queueTask(new FutureTask<Void>(task, null));
    if (future == null) {
      throw new RuntimeException("Failed to queue task: " + task);
    }
    return future;
  }

  public <T> Future<T> submit(Callable<T> task) {
    final RunnableFuture<T> future = queueTask(new FutureTask<T>(task));
    if (future == null) {
      throw new RuntimeException("Failed to queue task: " + task);
    }
    return future;
  }

  @Nullable
  private <T> RunnableFuture<T> queueTask(FutureTask<T> futureTask) {
    if (myTaskQueue.offer(futureTask)) {
      processQueue();
      return futureTask;
    }
    return null;
  }

  protected void processQueue() {
    while (true) {
      final int count = myInProgress.get();
      if (count >= myMaxTasks) {
        return;
      }
      if (myInProgress.compareAndSet(count, count + 1)) {
        break;
      }
    }
    myBackendExecutor.execute(USER_TASK_RUNNER);
  }
}
