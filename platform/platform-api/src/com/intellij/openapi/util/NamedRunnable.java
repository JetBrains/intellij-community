// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill.Kalishev
 */
public abstract class NamedRunnable implements Runnable {
  private static final Logger LOG = Logger.getInstance(NamedRunnable.class);
  private final String myName;

  protected NamedRunnable(@NotNull String name) {
    myName = name;
  }

  protected void trace(Object message) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(myName + ": " + message);
    }
  }

  @Override
  public String toString() {
    return myName;
  }
}
