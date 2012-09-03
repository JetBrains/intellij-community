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

import com.intellij.util.ArrayUtil;
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

  void setExecuted() {
    signalExecution();
    if (isExecuted()) {
      Runnable[] all;
      synchronized (this) {
        if (myRunnables == null) {
          all = ArrayUtil.EMPTY_RUNNABLE_ARRAY;
        }
        else {
          all = myRunnables.toArray(new Runnable[myRunnables.size()]);
          myRunnables.clear();
        }
      }
      for (Runnable each : all) {
        each.run();
      }
    }
  }

  private static class CompositeRunnable extends ArrayList<Runnable> implements Runnable {
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

  final void doWhenExecuted(@NotNull final Runnable runnable) {
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
        toRun = EmptyRunnable.getInstance();
        if (myRunnables == null) {
          myRunnables = new SmartList<Runnable>();
        }

        myRunnables.add(runnable);
      }
    }

    toRun.run();
  }


  private synchronized void signalExecution() {
    myCurrentCount++;
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
