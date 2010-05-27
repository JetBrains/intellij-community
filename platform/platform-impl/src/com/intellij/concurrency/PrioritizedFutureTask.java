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

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<PrioritizedFutureTask> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.PrioritizedFutureTask");
  private final JobImpl<T> myJob;
  private final long myJobIndex;
  private final int myTaskIndex;
  private final int myPriority;
  private final boolean myFailFastOnAcquireReadAction;
  private volatile boolean myParentThreadHasReadAccess;
  private volatile boolean myReportExceptions;

  PrioritizedFutureTask(final Callable<T> callable,
                        JobImpl<T> job,
                        long jobIndex,
                        int taskIndex,
                        int priority,
                        boolean failFastOnAcquireReadAction) {
    super(callable);
    myJob = job;
    myJobIndex = jobIndex;
    myTaskIndex = taskIndex;
    myPriority = priority;
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
  }

  public void beforeRun(boolean parentThreadHasReadAccess, boolean reportExceptions) {
    myParentThreadHasReadAccess = parentThreadHasReadAccess;
    myReportExceptions = reportExceptions;
  }

  @Override
  public void run() {
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          if (myJob.isCanceled()) {
            //set(null);
            cancel(false); //todo cancel or set?
          }
          else {
            PrioritizedFutureTask.super.run();
          }
        }
        finally {
          try {
            if (myReportExceptions) {
              // let exceptions during execution manifest themselves
              PrioritizedFutureTask.super.get();
            }
          }
          catch (CancellationException ignored) {
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
          finally {
            myJob.taskDone();
            //myDoneCondition.up();
          }
        }
      }
    };
    if (myParentThreadHasReadAccess) {
      if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
        // all tests unfortunately are run from within write action, so they cannot really run read action in any thread
        ApplicationImpl.setExceptionalThreadWithReadAccessFlag(true);
      }
      // have to start "real" read action so that we cannot start write action until we are finished here
      if (myFailFastOnAcquireReadAction) {
        if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(runnable)) {
          myJob.cancel();
        }
      }
      else {
        // cannot runreadaction here because of possible deadlock when writeaction in the queue
        boolean old = ApplicationImpl.setExceptionalThreadWithReadAccessFlag(true);
        try {
          runnable.run();
        }
        finally {
          ApplicationImpl.setExceptionalThreadWithReadAccessFlag(old);
        }
      }
    }
    else {
      runnable.run();
    }
  }

  public int compareTo(final PrioritizedFutureTask o) {
    int priorityDelta = myPriority - o.myPriority;
    if (priorityDelta != 0) return priorityDelta;
    if (myJobIndex != o.myJobIndex) return myJobIndex < o.myJobIndex ? -1 : 1;
    return myTaskIndex - o.myTaskIndex;
  }
}
