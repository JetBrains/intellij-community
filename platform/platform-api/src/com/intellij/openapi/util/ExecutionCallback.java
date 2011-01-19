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
    if (myExecuted.isExecuted()) {
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
