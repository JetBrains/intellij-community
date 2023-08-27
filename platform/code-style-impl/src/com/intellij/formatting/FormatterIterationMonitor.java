// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Prevents formatter from endlessly doing some iterations for some specific and probably erroneous models, for e.g. with
 * unrestricted block nesting, wrapping, etc.
 */
public final class FormatterIterationMonitor<T> {
  private static final Logger LOG = Logger.getInstance(FormatterIterationMonitor.class);

  private int myIterations;
  private final int myMaxIterations;
  private final T myFallbackValue;

  public FormatterIterationMonitor(int maxIterations, @NotNull T fallbackValue) {
    myMaxIterations = maxIterations;
    myFallbackValue = fallbackValue;
  }

  public boolean iterate() {
    int newValue = myIterations + 1;
    if (myIterations >= myMaxIterations) {
      LOG.debug("Iterations limit " + myMaxIterations + " reached: ", new Throwable());
      return false;
    }
    myIterations = newValue;
    return true;
  }

  public @NotNull T getFallbackValue() {
    return myFallbackValue;
  }
}
