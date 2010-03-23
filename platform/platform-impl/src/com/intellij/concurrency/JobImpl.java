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
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class JobImpl<T> implements Job<T> {
  private final String myTitle;
  private final List<Callable<T>> myTasks = ContainerUtil.createEmptyCOWList(); 
  private final long myJobIndex = JobSchedulerImpl.currentJobIndex();
  private final int myPriority;
  private final List<PrioritizedFutureTask<T>> myFutures = ContainerUtil.createEmptyCOWList();
  private volatile boolean myCanceled = false;

  public JobImpl(String title, int priority) {
    myTitle = title;
    myPriority = priority;
  }

  public String getTitle() {
    return myTitle;
  }

  public void addTask(Callable<T> task) {
    checkNotStarted();

    myTasks.add(task);
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

    createFutures(callerHasReadAccess, false);

    // Don't bother scheduling if we only have one processor or only one task
    if (JobSchedulerImpl.CORES_COUNT >= 2 && myFutures.size() >= 2) {
      for (PrioritizedFutureTask<T> future : myFutures) {
        JobSchedulerImpl.execute(future);
      }
    }

    // http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
    for (PrioritizedFutureTask<T> future : myFutures) {
      future.run();
    }

    return waitForTermination();
  }

  private void createFutures(boolean callerHasReadAccess, final boolean reportExceptions) {
    int startTaskIndex = JobSchedulerImpl.currentTaskIndex();
    for (final Callable<T> task : myTasks) {
      final PrioritizedFutureTask<T> future = new PrioritizedFutureTask<T>(task, myJobIndex, startTaskIndex++, myPriority, callerHasReadAccess,
                                                                           reportExceptions);
      myFutures.add(future);
    }
  }

  public List<T> waitForTermination() throws Throwable {
    List<T> results = new ArrayList<T>(myFutures.size());

    Throwable ex = null;
    for (Future<T> f : myFutures) {
      try {
        T result = null;
        while(true) {
          try {
            result = f.get(10, TimeUnit.MILLISECONDS);
            break;
          }
          catch (TimeoutException e) {
            if (f.isDone() || f.isCancelled()) break;
          }
        }
        results.add(result);
      }
      catch (CancellationException ignore) {
      }
      catch (ExecutionException e) {
        cancel();

        Throwable cause = e.getCause();
        if (cause != null) {
          ex = cause;
        }
      }
    }

    // Future.get() exits when currently running is canceled, thus awaiter may get control before spawned tasks actually terminated,
    // that's why additional join logic.
    for (PrioritizedFutureTask<T> future : myFutures) {
      future.awaitTermination();
    }

    if (ex != null) throw ex;

    return results;
  }

  public void cancel() {
    checkScheduled();
    if (myCanceled) return;
    myCanceled = true;

    for (Future<T> future : myFutures) {
      future.cancel(false);
    }
  }

  public boolean isCanceled() {
    checkScheduled();
    return myCanceled;
  }

  public void schedule() {
    checkCanSchedule();

    createFutures(false, true);

    for (PrioritizedFutureTask<T> future : myFutures) {
      JobSchedulerImpl.execute(future);
    }
  }

  public boolean isDone() {
    checkScheduled();

    for (Future<T> future : myFutures) {
      if (!future.isDone()) return false;
    }

    return true;
  }

  private void checkCanSchedule() {
    checkNotStarted();
    if (myTasks.isEmpty()) {
      throw new IllegalStateException("No tasks to run. You can't schedule a job which has no tasks");
    }
  }

  private void checkNotStarted() {
    if (!myFutures.isEmpty()) {
      throw new IllegalStateException("Already running. You can't call this method for a job which is already scheduled");
    }
  }

  private void checkScheduled() {
    if (myFutures.isEmpty()) {
      throw new IllegalStateException("Cannot call this method for not yet started job");
    }
  }
}
