package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

class ExecutionCallback {
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

  final void doWhenExecuted(@NotNull final Runnable runnable) {
    if (myRunnables == null) {
      myRunnables = new ArrayList<Runnable>();
    }

    myRunnables.add(runnable);

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
      final Runnable[] all = myRunnables.toArray(new Runnable[myRunnables.size()]);
      myRunnables.clear();
      for (Runnable each : all) {
        each.run();
      }
    }
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
  }

}
