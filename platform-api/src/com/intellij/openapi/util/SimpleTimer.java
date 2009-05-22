package com.intellij.openapi.util;

import java.util.Timer;
import java.util.TimerTask;

public class SimpleTimer {

  private Timer ourTimer = new Timer("SimpleTimer", true);

  private static SimpleTimer ourInstance = new SimpleTimer();

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

}