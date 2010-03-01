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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

public abstract class Timer implements Disposable, Runnable  {

  private final int mySpan;

  private volatile boolean myRunning;
  private volatile boolean myDisposed;
  private volatile boolean myRestartRequest;

  private final String myName;

  private volatile boolean myTakeInitialDelay = true;
  private volatile boolean myInitiallySlept = false;

  private ThreadRunner myRunner;
  private Exception myInterruptedException;

  private final Object LOCK = new Object();

  public Timer(String name, int span) {
    myName = name;
    mySpan = span;
  }

  public void setTakeInitialDelay(final boolean take) {
    myTakeInitialDelay = take;
  }

  public final int getSpan() {
    return mySpan;
  }

  public final void start() {
    synchronized (LOCK) {
      myRunning = true;

      if (myRunner != null) return;

      Application app = ApplicationManager.getApplication();
      myRunner = app != null ? new AppPool() : new PlainThread();
      myRunner.run(this);
    }
  }

  public final void run() {
    try {
      while(true) {
        synchronized (LOCK) {
          if (!myRunning || myDisposed) {
            myRunner = null;
            resetToStart();
            break;
          }
        }

        if (myTakeInitialDelay || myInitiallySlept) {
          Thread.currentThread().sleep(mySpan);
        }
        myInitiallySlept = true;

        if (myRestartRequest) {
          myRestartRequest = false;
          continue;
        }

        onTimer();
      }
    }
    catch (InterruptedException e) {
      myInterruptedException = e;
      resetToStart();
    }
  }

  private void resetToStart() {
    myRestartRequest = false;
    myInitiallySlept = false;
  }

  protected abstract void onTimer() throws InterruptedException;

  public final void suspend() {
    synchronized (LOCK) {
      if (myDisposed) return;

      if (myRunning) {
        myRunning = false;
      } else {
        resetToStart();
      }
    }
  }

  public final void resume() {
    synchronized (LOCK) {
      if (myDisposed) return;

      start();
    }
  }

  public final void dispose() {
    synchronized (LOCK) {
      myDisposed = true;
      suspend();
    }
  }

  public void restart() {
    synchronized (LOCK) {
      start();
      myRestartRequest = true;
    }
  }

  public boolean isRunning() {
    synchronized (LOCK) {
      return myRunning;
    }
  }

  public boolean isDisposed() {
    synchronized (LOCK) {
      return myDisposed;
    }
  }

  interface ThreadRunner{
    void run(Runnable runnable);
  }

  public String toString() {
    return "Timer=" + myName;
  }

  static class AppPool implements ThreadRunner {
    public void run(Runnable runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  }

  static class PlainThread implements ThreadRunner {

    private Thread myThread;

    public void run(Runnable runnable) {
      myThread = new Thread(runnable, "timer thread");
      myThread.start();
    }
  }

}
