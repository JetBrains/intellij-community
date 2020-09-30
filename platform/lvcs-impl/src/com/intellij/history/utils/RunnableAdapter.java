// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.utils;

import com.intellij.util.ExceptionUtil;

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
