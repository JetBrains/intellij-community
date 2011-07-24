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
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class Timer implements Disposable, Runnable  {
  private final int mySpan;

  private final String myName;

  private volatile boolean myTakeInitialDelay = true;

  private Exception myInterruptedException;

  private final Object LOCK = new Object();

  private SharedThread mySharedThread;

  enum TimerState {startup, intialSleep, running, suspended, restarting, pausing, disposed}

  private TimerState myState = TimerState.startup;

  private int myPauseTime;

  public Timer(@NonNls String name, int span) {
    myName = name;
    mySpan = span;
    mySharedThread = SharedThread.getInstance();
  }

  public void setTakeInitialDelay(final boolean take) {
    myTakeInitialDelay = take;
  }

  public final int getSpan() {
    return mySpan;
  }

  public final void start() {
    synchronized (LOCK) {
      if (isRunning() || isDisposed()) return;

      myState = TimerState.startup;
      mySharedThread.queue(this, 0);
    }
  }

  public void run() {
    synchronized (LOCK) {
      switch (myState) {
        case startup:
          startup();
          break;
        case restarting:
          startup();
          break;
        case intialSleep:
          myState = TimerState.running;
          fireAndReschedule();
          break;
        case running:
          fireAndReschedule();
          break;
        case suspended:
          break;
        case pausing:
          myState = TimerState.running;
          mySharedThread.queue(this, myPauseTime);
          break;
        case disposed:
          break;
      }
    }
  }

  private void startup() {
    myState = TimerState.intialSleep;
    if (myTakeInitialDelay) {
      mySharedThread.queue(this, mySpan);
    } else {
      fireAndReschedule();
    }
  }

  private void fireAndReschedule() {
    try {
      onTimer();
    }
    catch (InterruptedException e) {
      myInterruptedException = e;
      suspend();
      return;
    }
    mySharedThread.queue(this, getSpan());
  }

  protected abstract void onTimer() throws InterruptedException;

  public final void suspend() {
    synchronized (LOCK) {
      if (isDisposed() || !isRunning()) return;
      myState = TimerState.suspended;
    }
  }

  public final void delay(int length) {
    synchronized (LOCK) {
      if (isDisposed() || !isRunning()) return;
      myState = TimerState.pausing;
      mySharedThread.queue(this, length);
    }
  }

  public final void resume() {
    synchronized (LOCK) {
      if (isDisposed() || isRunning()) return;
      myState = TimerState.running;
      mySharedThread.queue(this, 0);
    }
  }

  public final void dispose() {
    synchronized (LOCK) {
      myState = TimerState.disposed;
    }
  }

  public void restart() {
    synchronized (LOCK) {
      myState = TimerState.restarting;
      mySharedThread.queue(this, 0);
    }
  }

  public boolean isRunning() {
    synchronized (LOCK) {
      return myState == TimerState.running || myState == TimerState.intialSleep || myState == TimerState.restarting;
    }
  }

  public boolean isDisposed() {
    synchronized (LOCK) {
      return myState == TimerState.disposed;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Timer=" + myName;
  }

  private static class SharedThread {

    private final ScheduledThreadPoolExecutor myExecutor;
    private static SharedThread ourInstance;

    private SharedThread() {
      myExecutor = ConcurrencyUtil.newSingleScheduledThreadExecutor("AnimatorThread");
    }

    public static SharedThread getInstance() {
      if (ourInstance == null) {
        ourInstance = new SharedThread();
      }
      return ourInstance;
    }

    public void queue(Timer timer, int span) {
      myExecutor.remove(timer);
      myExecutor.schedule(timer, span, TimeUnit.MILLISECONDS);
    }
  }

  public static void main(String[] args) {
    final Timer timer = new MyTimer();

    timer.setTakeInitialDelay(false);
    timer.start();
  }

  private static class MyTimer extends Timer {
    private final int[] myCount;

    public MyTimer() {
      super("", 500);
      myCount = new int[] {3};
    }

    @Override
    protected void onTimer() throws InterruptedException {
      System.out.println("Timer.onTimer");
      myCount[0]--;
      if (myCount[0] == 0) {
        suspend();
        new Thread() {
          @Override
          public void run() {
            try {
              Thread.currentThread().sleep(2000);
            }
            catch (InterruptedException e) {
              e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            myCount[0] = 3;
            MyTimer.this.restart();
          }
        }.start();
      }
    }
  }
}
