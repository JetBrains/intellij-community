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

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@NonNls
public class JobSchedulerImpl extends JobScheduler implements Disposable {
  public static final int CORES_COUNT = /*1;//*/ Runtime.getRuntime().availableProcessors();

  private static final ThreadFactory WORKERS_FACTORY = new ThreadFactory() {
    private int threadSeq;

    @NotNull
    @Override
    public synchronized Thread newThread(@NotNull final Runnable r) {
      @NonNls String name = "JobScheduler pool " + threadSeq + "/" + CORES_COUNT;
      final Thread thread = new Thread(r, name);
      thread.setPriority(Thread.NORM_PRIORITY);
      threadSeq++;
      return thread;
    }
  };

  private static final PriorityBlockingQueue<Runnable> ourQueue = new PriorityBlockingQueue<Runnable>();
  private static final MyExecutor ourExecutor = new MyExecutor();

  static int currentTaskIndex() {
    return ourQueue.size();
  }

  @Override
  public void dispose() {
    ((ThreadPoolExecutor)getScheduler()).getQueue().clear();
  }

  static Runnable stealTask() {
    return ourQueue.poll();
  }

  static void submitTask(@NotNull PrioritizedFutureTask future, boolean runInReadAction, boolean reportExceptions) {
    future.beforeRun(runInReadAction, reportExceptions);
    ourExecutor.executeTask(future);
  }

  private static class MyExecutor extends ThreadPoolExecutor {
    private MyExecutor() {
      super(CORES_COUNT, Integer.MAX_VALUE, 60 * 10, TimeUnit.SECONDS, ourQueue, WORKERS_FACTORY);
    }

    private void executeTask(@NotNull PrioritizedFutureTask task) {
      super.execute(task);
    }

    @Override
    public void execute(@NotNull Runnable command) {
      throw new IllegalStateException("Use executeTask() to submit PrioritizedFutureTasks only");
    }
  }
}
