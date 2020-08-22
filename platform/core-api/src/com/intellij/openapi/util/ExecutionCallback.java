// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ExecutionCallback {
  private int myCurrentCount;
  private final int myCountToExecution;
  private List<Runnable> myRunnables;

  ExecutionCallback() {
    this(1);
  }

  ExecutionCallback(int executedCount) {
    myCountToExecution = executedCount;
  }

  /**
   * @return true if was executed, false if specified executed count is not yet done
   */
  boolean setExecuted() {
    if (!signalExecution()) {
      return false;
    }

    List<Runnable> all;
    synchronized (this) {
      if (myRunnables == null) {
        return true;
      }
      else {
        all = myRunnables;
        myRunnables = null;
      }
    }
    for (Runnable each : all) {
      each.run();
    }
    return true;
  }

  private static final class CompositeRunnable extends ArrayList<Runnable> implements Runnable {
    private CompositeRunnable(@NotNull Collection<? extends Runnable> c) {
      super(c);
    }

    @Override
    public void run() {
      for (Runnable runnable : this) {
        runnable.run();
      }
    }
  }

  void doWhenExecuted(@NotNull final Runnable runnable) {
    Runnable toRun;
    synchronized (this) {
      if (isExecuted()) {
        if (myRunnables == null) {
          toRun = runnable;
        }
        else {
          CompositeRunnable composite = new CompositeRunnable(myRunnables);
          composite.add(runnable);
          toRun = composite;
          myRunnables = null;
        }
      }
      else {
        if (myRunnables == null) {
          myRunnables = new SmartList<>();
        }

        myRunnables.add(runnable);
        return;
      }
    }

    toRun.run();
  }

  /**
   * Avoid memory leak in case: myDone executed but myRejected still keep doWhenRejected listeners (and vice versa)
   */
  synchronized void clear() {
    myRunnables = null;
  }

  private synchronized boolean signalExecution() {
    return ++myCurrentCount >= myCountToExecution;
  }

  synchronized boolean isExecuted() {
    return myCurrentCount >= myCountToExecution;
  }

  @NonNls
  @Override
  public synchronized String toString() {
    return "current=" + myCurrentCount + " countToExecution=" + myCountToExecution;
  }
}