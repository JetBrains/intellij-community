package com.intellij.openapi.util;

import com.intellij.util.ui.UIUtil;

public abstract class EdtRunnable implements Runnable {

  public final void run() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        runEdt();
      }
    });
  }

  public abstract void runEdt();
}