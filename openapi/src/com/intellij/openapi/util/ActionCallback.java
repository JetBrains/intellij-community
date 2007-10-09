/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

public class ActionCallback {

  private boolean myDone;
  private List<java.lang.Runnable> myRunnables;

  public void setDone() {
    myDone = true;
    callback();
  }

  public final ActionCallback doWhenDone(@NotNull final java.lang.Runnable runnable) {
    if (myRunnables == null) {
      myRunnables = new ArrayList<java.lang.Runnable>();
    }

    myRunnables.add(runnable);

    callback();

    return this;
  }

  public final void markDone(final ActionCallback child) {
    doWhenDone(new java.lang.Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  private void callback() {
    if (myDone && myRunnables != null) {
      final java.lang.Runnable[] all = myRunnables.toArray(new java.lang.Runnable[myRunnables.size()]);
      myRunnables.clear();
      for (java.lang.Runnable each : all) {
        each.run();
      }
    }
  }

  public static class Done extends ActionCallback {
    public Done() {
      setDone();
    }
  }

  public interface Runnable {
    ActionCallback run();
  }
}
