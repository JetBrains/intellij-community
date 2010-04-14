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
package com.intellij.openapi.util;

public class SimpleTimerTask {

  private final long myTargetTime;
  private final Runnable myRunnable;

  private boolean myCancelled;

  private final Object LOCK = new Object();
  private final SimpleTimer myTimer;

  public SimpleTimerTask(long targetTime, Runnable runnable, SimpleTimer timer) {
    myTargetTime = targetTime;
    myRunnable = runnable;
    myTimer = timer;
  }

  public void cancel() {
    synchronized (LOCK) {
      myCancelled = true;
      myTimer.onCancelled(this);
    }
  }

  public boolean isCancelled() {
    synchronized (LOCK) {
      return myCancelled;
    }
  }

  public void run() {
    synchronized (LOCK) {
      if (!myCancelled) {
        myRunnable.run();
      }
    }
  }

  long getTargetTime() {
    return myTargetTime;
  }

  @Override
  public String toString() {
    return "targetTime=" + myTargetTime + " cancelled=" + myCancelled + " runnable=" + myRunnable;
  }
}
