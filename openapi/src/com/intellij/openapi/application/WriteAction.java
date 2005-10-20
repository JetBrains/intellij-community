/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import javax.swing.*;
import java.awt.*;

public abstract class WriteAction<T> extends BaseActionRunnable<T> {

  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<T>(this);

    if (canWriteNow()) {
      result.run();
      return result;
    }

    try {
      if (EventQueue.isDispatchThread()) {
        getApplication().runWriteAction(new Runnable() {
          public void run() {
            result.run();
          }
        });
      } else {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            getApplication().runWriteAction(new Runnable() {
              public void run() {
                result.run();
              }
            });
          }
        });
      }
    } catch (Exception e) {
      if (isSilentExecution()) {
        result.setThrowable(e);
      } else {
        throw new Error(e);
      }
    }
    return result;
  }

}
