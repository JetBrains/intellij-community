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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JobImpl<T> implements Job<T> {
  private static volatile long ourJobsCounter = 0;
  private final long myJobIndex = ourJobsCounter++;
  private final int myPriority;
  private final List<PrioritizedFutureTask<T>> myFutures = new ArrayList<PrioritizedFutureTask<T>>();
  private volatile boolean canceled = false;
  private final AtomicInteger runningTasks = new AtomicInteger();
  private volatile boolean scheduled;
  private final boolean myFailFastOnAcquireReadAction;

  JobImpl(int priority, boolean failFastOnAcquireReadAction) {
    myPriority = priority;
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
  }

  public String getTitle() {
    return null;
  }

  public void addTask(Callable<T> task) {
    checkNotScheduled();

    PrioritizedFutureTask<T> future =
      new PrioritizedFutureTask<T>(task, this, myJobIndex, JobSchedulerImpl.currentTaskIndex(), myPriority, myFailFastOnAcquireReadAction);
    synchronized (myFutures) {
      myFutures.add(future);
    }
    runningTasks.incrementAndGet();
  }

  public void addTask(Runnable task, T result) {
    addTask(Executors.callable(task, result));
  }

  public void addTask(Runnable task) {
    addTask(Executors.callable(task, (T)null));
  }

  public List<T> scheduleAndWaitForResults() throws Throwable {
    checkCanSchedule();
    final Application application = ApplicationManager.getApplication();
    boolean callerHasReadAccess = application != null && application.isReadAccessAllowed();

    // Don't bother scheduling if we only have one processor or only one task
    boolean reallySchedule;
    PrioritizedFutureTask[] tasks = getTasks();
    synchronized (myFutures) {
      reallySchedule = JobSchedulerImpl.CORES_COUNT >= 2 && myFutures.size() >= 2;
    }
    scheduled = true;

    if (!reallySchedule) {
      for (PrioritizedFutureTask future : tasks) {
        future.run();
      }
      return null;
    }

    submitTasks(tasks, callerHasReadAccess, false);

    while (!isDone() && JobSchedulerImpl.stealAndRunTask()) {
      int i = 0;
    }

    // in case of imbalanced tasks one huge task can stuck running and we would fall to waitForTermination instead of doing useful work
    //// http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
    //for (PrioritizedFutureTask task : tasks) {
    //  task.run();
    //}
    //

    waitForTermination(tasks);
    return null;
  }

  public void waitForTermination(PrioritizedFutureTask[] tasks) throws Throwable {
    Throwable ex = null;
    try {
      for (PrioritizedFutureTask f : tasks) {
        // this loop is for workaround of mysterious bug
        // when sometimes future hangs inside parkAndCheckForInterrupt() during unbounded get()
        while(true) {
          try {
            f.get(10, TimeUnit.MILLISECONDS);
            break;
          }
          catch (TimeoutException e) {
            if (f.isDone()) {
              f.get(); // does awaitTermination(), and there is no chance to hang
              break;
            }
          }
        }
      }
    }
    catch (CancellationException ignore) {
      // already cancelled
    }
    catch (ExecutionException e) {
      cancel();

      Throwable cause = e.getCause();
      if (cause != null) {
        ex = cause;
      }
    }

    if (ex != null) {
      throw ex;
    }
  }

  public void cancel() {
    checkScheduled();
    if (canceled) return;
    canceled = true;

    PrioritizedFutureTask[] tasks = getTasks();
    for (PrioritizedFutureTask future : tasks) {
      future.cancel(false);
    }
    runningTasks.set(0);
  }

  public boolean isCanceled() {
    checkScheduled();
    return canceled;
  }

  public void schedule() {
    checkCanSchedule();
    scheduled = true;

    PrioritizedFutureTask[] tasks = getTasks();

    submitTasks(tasks, false, true);
  }

  public PrioritizedFutureTask[] getTasks() {
    PrioritizedFutureTask[] tasks;
    synchronized (myFutures) {
      tasks = myFutures.toArray(new PrioritizedFutureTask[myFutures.size()]);
    }
    return tasks;
  }

  public boolean isDone() {
    checkScheduled();

    return runningTasks.get() <= 0;
  }

  private void checkCanSchedule() {
    checkNotScheduled();
    synchronized (myFutures) {
      if (myFutures.isEmpty()) {
        throw new IllegalStateException("No tasks added. You can't schedule a job which has no tasks");
      }
    }
  }

  private void checkNotScheduled() {
    if (scheduled) {
      throw new IllegalStateException("Already running. You can't call this method for a job which is already scheduled");
    }
  }

  private void checkScheduled() {
    if (!scheduled) {
      throw new IllegalStateException("Cannot call this method for not yet started job");
    }
  }

  private static void submitTasks(PrioritizedFutureTask[] tasks, boolean callerHasReadAccess, boolean reportExceptions) {
    for (final PrioritizedFutureTask future : tasks) {
      JobSchedulerImpl.submitTask(future, callerHasReadAccess, reportExceptions);
    }
  }

  void taskDone() {
    runningTasks.decrementAndGet();
  }

}
