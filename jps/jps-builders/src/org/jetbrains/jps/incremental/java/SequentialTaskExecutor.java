package org.jetbrains.jps.incremental.java;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
 */
public class SequentialTaskExecutor {

  private final ExecutorService myExecutor;
  private final Queue<Runnable> myTaskQueue = new LinkedBlockingQueue<Runnable>();
  private final AtomicBoolean myInProgress = new AtomicBoolean(false);
  private final Runnable USER_TASK_RUNNER = new Runnable() {
    public void run() {
      final Runnable task = myTaskQueue.poll();
      try {
        if (task != null) {
          task.run();
        }
      }
      finally {
        myInProgress.set(false);
        if (!myTaskQueue.isEmpty()) {
          processQueue();
        }
      }
    }
  };

  public SequentialTaskExecutor(ExecutorService executor) {
    myExecutor = executor;
  }

  public void submit(Runnable task) {
    if (myTaskQueue.offer(task)) {
      processQueue();
    }
    else {
      throw new RuntimeException("Failed to queue task: " + task);
    }
  }

  private void processQueue() {
    if (!myInProgress.getAndSet(true)) {
      myExecutor.submit(USER_TASK_RUNNER);
    }
  }

}
