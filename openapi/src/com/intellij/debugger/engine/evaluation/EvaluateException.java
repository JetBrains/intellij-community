/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.evaluation;

import com.intellij.openapi.diagnostic.Logger;

public class EvaluateException extends Exception {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.EvaluateException");

  public EvaluateException(String msg, Throwable th) {
    super(msg, th);
    if (LOG.isDebugEnabled()) {
      LOG.debug(msg);
    }
  }
}