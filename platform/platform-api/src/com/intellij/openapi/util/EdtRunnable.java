// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.ui.UIUtil;

public abstract class EdtRunnable implements ExpirableRunnable {
  private boolean myExpired;

  @Override
  public final void run() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!isExpired()) {
        runEdt();
      }
    });
  }

  public void expire() {
    myExpired = true;
  }

  @Override
  public boolean isExpired() {
    return myExpired;
  }

  public abstract void runEdt();
}