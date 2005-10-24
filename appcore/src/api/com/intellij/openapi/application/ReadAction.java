/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.awt.*;

public abstract class ReadAction<T> extends BaseActionRunnable<T> {

  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<T>(this);

    if (canReadNow()) {
      result.run();
      return result;
    }

    if (EventQueue.isDispatchThread()) {
      return result.run();
    } else {
      getApplication().runReadAction(new Runnable() {
        public void run() {
          result.run();
        }
      });
    }

    return result;
  }

}
