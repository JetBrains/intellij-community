package com.intellij.openapi.util;

public class SimpleTimerTask {

  private final long myTargetTime;
  private final Runnable myRunnable;

  private boolean myCancelled;

  public SimpleTimerTask(long targetTime, Runnable runnable) {
    myTargetTime = targetTime;
    myRunnable = runnable;
  }

  public void cancel() {
    myCancelled = true;
  }

  public boolean isCancelled() {
    return myCancelled;
  }

  public void run() {
    myRunnable.run();
  }

  @Override
  public String toString() {
    return "targetTime=" + myTargetTime + " cancelled=" + myCancelled + " runnable=" + myRunnable;
  }
}