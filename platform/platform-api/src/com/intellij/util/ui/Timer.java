// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated use {@link AppExecutorUtil#getAppScheduledExecutorService()} instead
 */
@Deprecated(forRemoval = true)
public abstract class Timer implements Disposable, Runnable  {
  private final int mySpan;

  private final String myName;

  private volatile boolean myTakeInitialDelay = true;

  private Exception myInterruptedException;

  private final Object LOCK = new Object();

  private ScheduledFuture<?> myFuture;

  private enum TimerState {startup, initialSleep, running, suspended, restarting, pausing, disposed}

  private TimerState myState = TimerState.startup;

  private int myPauseTime;

  public Timer(@NonNls String name, int span) {
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
      if (isRunning() || isDisposed()) return;

      myState = TimerState.startup;
      queue(this, 0);
    }
  }

  @Override
  public void run() {
    synchronized (LOCK) {
      switch (myState) {
        case startup:
          startup();
          break;
        case restarting:
          startup();
          break;
        case initialSleep:
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
          queue(this, myPauseTime);
          break;
        case disposed:
          break;
      }
    }
  }

  private void startup() {
    myState = TimerState.initialSleep;
    if (myTakeInitialDelay) {
      queue(this, mySpan);
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
    queue(this, getSpan());
  }

  protected abstract void onTimer() throws InterruptedException;

  public final void suspend() {
    synchronized (LOCK) {
      if (isDisposed() || !isRunning()) return;
      myState = TimerState.suspended;
    }
  }

  public final void resume() {
    synchronized (LOCK) {
      if (isDisposed() || isRunning()) return;
      myState = TimerState.running;
      queue(this, 0);
    }
  }

  @Override
  public final void dispose() {
    synchronized (LOCK) {
      myState = TimerState.disposed;
    }
  }

  public void restart() {
    synchronized (LOCK) {
      myState = TimerState.restarting;
      queue(this, 0);
    }
  }

  private boolean isRunning() {
    synchronized (LOCK) {
      return myState == TimerState.running || myState == TimerState.initialSleep || myState == TimerState.restarting;
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

  private void setFuture(ScheduledFuture<?> schedule) {
    myFuture = schedule;
  }

  private static void queue(Timer timer, int span) {
    final ScheduledFuture<?> future = timer.myFuture;
    if (future != null) {
      future.cancel(true);
    }

    timer.setFuture(AppExecutorUtil.getAppScheduledExecutorService().schedule(timer, span, TimeUnit.MILLISECONDS));
  }
}
