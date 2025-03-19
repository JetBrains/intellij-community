// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

public abstract class DebuggerTaskImpl implements DebuggerTask {
  private int myHolds = 0;

  @Override
  public final synchronized void release() {
    if (myHolds > 0) {
      if (--myHolds == 0) {
        notifyAll();
      }
    }
  }

  @Override
  public final synchronized void hold() {
    myHolds++;
  }

  @Override
  public final synchronized void waitFor() {
    while (myHolds > 0) {
      try {
        wait();
      }
      catch (InterruptedException ignored) {
      }
    }
  }
}
