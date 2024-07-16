// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.utils;

import com.intellij.util.ExceptionUtil;

@Deprecated
public abstract class RunnableAdapter implements Runnable {
  @Override
  public void run() {
    try {
      doRun();
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
  }

  public abstract void doRun() throws Exception;
}
