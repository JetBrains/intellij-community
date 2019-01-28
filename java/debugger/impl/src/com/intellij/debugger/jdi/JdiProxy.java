// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import org.jetbrains.annotations.NotNull;

public abstract class JdiProxy {
  @NotNull protected final JdiTimer myTimer;
  private int myTimeStamp;

  public JdiProxy(@NotNull JdiTimer timer) {
    myTimer = timer;
    myTimeStamp = myTimer.getCurrentTime();
  }

  protected void checkValid() {
    if(!isValid()) {
      myTimeStamp = myTimer.getCurrentTime();
      clearCaches();
    }
  }

  /**
   * @deprecated for testing only
   * @return
   */
  @Deprecated
  public boolean isValid() {
    return myTimeStamp == myTimer.getCurrentTime();
  }

  protected abstract void clearCaches();
}
