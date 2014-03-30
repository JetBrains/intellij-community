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

import com.intellij.Patches;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public abstract class JobScheduler {
  private static final ScheduledThreadPoolExecutor ourScheduledExecutorService;
  private static final int TASK_LIMIT = 50;
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.JobScheduler");
  private static final ThreadLocal<Long> START = new ThreadLocal<Long>();
  private static final boolean DO_TIMING = true;

  static {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, ConcurrencyUtil.newNamedThreadFactory("Periodic tasks thread", true, Thread.NORM_PRIORITY)) {
      @Override
      protected void beforeExecute(Thread t, Runnable r) {
        if (DO_TIMING) {
          START.set(System.currentTimeMillis());
        }
      }

      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        if (DO_TIMING) {
          long elapsed = System.currentTimeMillis() - START.get();
          Object unwrapped;
          if (elapsed > TASK_LIMIT && (unwrapped = info(r)) != null) {
            @NonNls String msg = TASK_LIMIT + " ms execution limit failed for: " + unwrapped + "; elapsed time was " + elapsed +"ms";
            LOG.info(msg);
          }
        }
      }
    };
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    enableRemoveOnCancelPolicy(executor);
    ourScheduledExecutorService = executor;
  }

  private static Object info(Runnable r) {
    if (!(r instanceof FutureTask)) return r;
    Object sync = ReflectionUtil.getField(FutureTask.class, r, Object.class, "sync"); // FutureTask.sync in <=JDK7
    Object o = sync == null ? r : sync;
    Object callable = ReflectionUtil.getField(o.getClass(), o, Callable.class, "callable"); // FutureTask.callable or Sync.callable
    if (callable == null) return null;
    Object task = ReflectionUtil.getField(callable.getClass(), callable, Object.class, "task"); // java.util.concurrent.Executors.RunnableAdapter.task
    return task == null ? callable : task;
  }

  private static void enableRemoveOnCancelPolicy(ScheduledThreadPoolExecutor executor) {
    if (Patches.USE_REFLECTION_TO_ACCESS_JDK7) {
      try {
        Method setRemoveOnCancelPolicy = ScheduledThreadPoolExecutor.class.getDeclaredMethod("setRemoveOnCancelPolicy", boolean.class);
        setRemoveOnCancelPolicy.setAccessible(true);
        setRemoveOnCancelPolicy.invoke(executor, true);
      }
      catch (Exception ignored) {
      }
    }
  }

  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  @NotNull
  public static ScheduledExecutorService getScheduler() {
    return ourScheduledExecutorService;
  }
}