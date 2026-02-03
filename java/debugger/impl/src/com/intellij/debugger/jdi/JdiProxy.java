// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.jdi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class JdiProxy {
  protected final @NotNull JdiTimer myTimer;
  private int myTimeStamp;

  public JdiProxy(@NotNull JdiTimer timer) {
    myTimer = timer;
    myTimeStamp = myTimer.getCurrentTime();
  }

  protected void checkValid() {
    //noinspection TestOnlyProblems
    if (!isValid()) {
      myTimeStamp = myTimer.getCurrentTime();
      clearCaches();
    }
  }

  @TestOnly
  public boolean isValid() {
    return myTimeStamp == myTimer.getCurrentTime();
  }

  protected abstract void clearCaches();
}
