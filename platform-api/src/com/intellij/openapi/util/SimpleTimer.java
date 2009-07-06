package com.intellij.openapi.util;

import java.util.Timer;
import java.util.TimerTask;

public class SimpleTimer {

  private Timer ourTimer = new Timer(THREAD_NAME, true);

  private static SimpleTimer ourInstance = new SimpleTimer();
  private static final String THREAD_NAME = "SimpleTimer";

  public static SimpleTimer getInstance() {
    return ourInstance;
  }

  public TimerTask setUp(final Runnable runnable, long timeMs) {
    final TimerTask task = new TimerTask() {
      public void run() {
        runnable.run();
      }
    };
    ourTimer.schedule(task, timeMs);

    return task;
  }

  public boolean isTimerThread() {
    return isTimerThread(Thread.currentThread());
  }

  public boolean isTimerThread(Thread thread) {
    return THREAD_NAME.equals(thread.getName());
  }

}