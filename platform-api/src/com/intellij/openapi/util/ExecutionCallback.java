package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ExecutionCallback {
  private final Executed myExecuted;
  private List<Runnable> myRunnables;

  ExecutionCallback() {
    myExecuted = new Executed(1);
  }

  ExecutionCallback(int executedCount) {
    myExecuted = new Executed(executedCount);
  }

  public void setExecuted() {
    myExecuted.signalExecution();
    callback();
  }

  public boolean isExecuted() {
    return myExecuted.isExecuted();
  }

  final void doWhenExecuted(@NotNull final Runnable runnable) {
    synchronized (this) {
      if (myRunnables == null) {
        myRunnables = new ArrayList<Runnable>();
      }

      myRunnables.add(runnable);
    }

    callback();
  }

  final void notifyWhenExecuted(final ActionCallback child) {
    doWhenExecuted(new Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  private void callback() {
    if (myExecuted.isExecuted() && myRunnables != null) {
      Runnable[] all;
      synchronized (this) {
        all = myRunnables.toArray(new Runnable[myRunnables.size()]);
        myRunnables.clear();
      }
      for (Runnable each : all) {
        each.run();
      }
    }
  }

  @Override
  public String toString() {
    return myExecuted.toString();
  }

  private static class Executed {
    int myCurrentCount;
    int myCountToExecution;

    Executed(final int countToExecution) {
      myCountToExecution = countToExecution;
    }

    void signalExecution() {
      myCurrentCount++;
    }

    boolean isExecuted() {
      return myCurrentCount >= myCountToExecution;
    }

    @Override
    public String toString() {
      return "current=" + myCurrentCount + " countToExecution=" + myCountToExecution;
    }
  }

}
