/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import org.jetbrains.annotations.*;

import java.util.concurrent.atomic.AtomicReference;

public class DiffTaskQueue implements Disposable {
  @NotNull private final Object LOCK = new Object();
  @NotNull private final Alarm myAlarm = new Alarm();

  private boolean myDisposed;
  @NotNull private final AtomicReference<ProgressIndicator> myProgressIndicator = new AtomicReference<ProgressIndicator>();

  @CalledInAny
  public void dispose() {
    // if EDT is awaiting for background progress in executeAndTryWait - it holds LOCK.
    // so we want to try cancel indicator before getting the lock.
    cancelProgress();
    synchronized (LOCK) {
      if (myDisposed) return;
      myDisposed = true;
      cancelProgress();
      Disposer.dispose(myAlarm);
    }
  }

  @CalledInAwt
  public void abort() {
    synchronized (LOCK) {
      cancelProgress();
      myAlarm.cancelAllRequests();
    }
  }

  private void cancelProgress() {
    ProgressIndicator indicator = myProgressIndicator.getAndSet(null);
    if (indicator != null) indicator.cancel();
  }

  @CalledInAwt
  public void abortAndSchedule(@NotNull final Runnable task, int millis) {
    synchronized (LOCK) {
      if (myDisposed) return;
      abort();

      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          synchronized (LOCK) {
            if (myDisposed) return;
          }
          task.run();
        }
      }, millis);
    }
  }

  @CalledInAwt
  public void executeAndTryWait(@NotNull final Function<ProgressIndicator, Runnable> backgroundTask,
                                @Nullable final Runnable onSlowAction,
                                final int waitMillis) {
    executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @CalledInAwt
  public void executeAndTryWait(@NotNull final Function<ProgressIndicator, Runnable> backgroundTask,
                                @Nullable final Runnable onSlowAction,
                                final int waitMillis,
                                final boolean forceEDT) {
    synchronized (LOCK) {
      if (myDisposed) return;
      abort();

      Function<ProgressIndicator, Runnable> function = new Function<ProgressIndicator, Runnable>() {
        @Override
        @CalledInBackground
        public Runnable fun(final ProgressIndicator indicator) {
          final Runnable callback = backgroundTask.fun(indicator);
          return new Runnable() {
            @Override
            @CalledInAwt
            public void run() {
              synchronized (LOCK) {
                if (myDisposed) return;
                indicator.checkCanceled();
              }
              callback.run();
            }
          };
        }
      };

      myProgressIndicator.set(BackgroundTaskUtil.executeAndTryWait(function, onSlowAction, waitMillis, forceEDT));
    }
  }
}
