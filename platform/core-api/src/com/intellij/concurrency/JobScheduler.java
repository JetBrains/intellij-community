/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.*;

public abstract class JobScheduler {
  private static final ScheduledThreadPoolExecutor ourScheduledExecutorService;

  static {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      public Thread newThread(final Runnable r) {
        final Thread thread = new Thread(r, "Periodic tasks thread");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
      }
    }) {
      private final boolean doTiming = true;
      @Override
      protected <V> RunnableScheduledFuture<V> decorateTask(final Runnable runnable, final RunnableScheduledFuture<V> task) {
        if (!doTiming) {
          return super.decorateTask(runnable, task);
        }
        return new ExecutionTimeCheckedTask<V>(task, runnable, ExecutionTimeCheckedTask.TASK_LIMIT);
      }

      @Override
      protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
        if (!doTiming) {
          return super.decorateTask(callable, task);
        }
        return new ExecutionTimeCheckedTask<V>(task, callable, ExecutionTimeCheckedTask.TASK_LIMIT);
      }
    };
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    ourScheduledExecutorService = executor;
  }

  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  public static ScheduledExecutorService getScheduler() {
    return ourScheduledExecutorService;
  }

  private static class ExecutionTimeCheckedTask<V> implements RunnableScheduledFuture<V> {
    private static final int TASK_LIMIT = 50;
    private static final Logger LOG = Logger.getInstance("#" + ExecutionTimeCheckedTask.class.getName());
    private final RunnableScheduledFuture<V> task;
    private final int limit;
    private final Object traceRunnableOrCallable;

    ExecutionTimeCheckedTask(RunnableScheduledFuture<V> _task, Object _traceRunnableOrCallable, int _limit) {
      task = _task;
      traceRunnableOrCallable = _traceRunnableOrCallable;
      limit = _limit;
    }

    @Override
    public boolean isPeriodic() {
      return task.isPeriodic();
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return task.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed o) {
      return task.compareTo(o);
    }

    @Override
    public void run() {
      long started = System.currentTimeMillis();
      try {
        task.run();
      } finally {
        long executionTime = System.currentTimeMillis() - started;
        if (executionTime > limit) {
          String msg = limit + " ms execution limit failed for:" + traceRunnableOrCallable + "," + executionTime;
          LOG.info(msg);
        }
      }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return task.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return task.isCancelled();
    }

    @Override
    public boolean isDone() {
      return task.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      return task.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return task.get(timeout, unit);
    }
  }
}