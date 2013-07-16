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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.concurrent.*;

public abstract class JobScheduler {
  private static final ScheduledThreadPoolExecutor ourScheduledExecutorService;
  private static final int TASK_LIMIT = 50;
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.JobScheduler");
  private static final ThreadLocal<Long> start = new ThreadLocal<Long>();

  static {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull final Runnable r) {
        final Thread thread = new Thread(r, "Periodic tasks thread");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
      }
    }) {
      private static final boolean doTiming = true;

      @Override
      protected void beforeExecute(Thread t, Runnable r) {
        if (doTiming) {
          start.set(System.currentTimeMillis());
        }
      }

      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        if (doTiming) {
          long elapsed = System.currentTimeMillis() - start.get();
          if (elapsed > TASK_LIMIT) {
            @NonNls String msg = TASK_LIMIT + " ms execution limit failed for: " + info(r) + "; elapsed time was " + elapsed +"ms";
            LOG.info(msg);
          }
        }
      }

      private Object info(Runnable r) {
        Object object = r;
        if (r instanceof FutureTask) {
          try {
            Field callableField = FutureTask.class.getDeclaredField("callable");
            callableField.setAccessible(true);
            object = callableField.get(r);
            Field task = object.getClass().getDeclaredField("task"); // java.util.concurrent.Executors.RunnableAdapter()
            task.setAccessible(true);
            object = task.get(object);
          }
          catch (Exception ignored) {
          }
        }
        return object;
      }
    };
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    ourScheduledExecutorService = executor;
  }

  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  @NotNull
  public static ScheduledExecutorService getScheduler() {
    return ourScheduledExecutorService;
  }
}