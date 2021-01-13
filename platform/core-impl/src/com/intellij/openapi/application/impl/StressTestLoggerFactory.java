// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StressTestLoggerFactory implements Logger.Factory {
  private final Logger.Factory testLoggerFactory = Logger.getFactory();

  StressTestLoggerFactory() {
    String expected = "com.intellij.testFramework.TestLoggerFactory";
    String actual = testLoggerFactory.getClass().getCanonicalName();
    if (!expected.equals(actual)) {
      throw new AssertionError("Unexpected logger factory class, " +
                               "expected :'" + expected + "' ," +
                               "actual: '" + actual + "'");
    }
  }

  @Override
  public @NotNull Logger getLoggerInstance(@NotNull String name) {
    return new StressTestLogger(
      testLoggerFactory.getLoggerInstance(name),
      org.apache.log4j.Logger.getLogger(name)
    );
  }

  /**
   * Required to override debug logging level enabled by default
   *
   * @see com.intellij.testFramework.TestLogger#isDebugEnabled()
   */
  private static final class StressTestLogger extends Logger {
    final org.apache.log4j.Logger log4jDelegate;
    final Logger delegate;

    StressTestLogger(Logger delegate, org.apache.log4j.Logger log4jDelegate) {
      this.log4jDelegate = log4jDelegate;
      this.delegate = delegate;
    }

    @Override
    public boolean isDebugEnabled() {
      return log4jDelegate.isDebugEnabled();
    }

    @Override
    public void debug(@NonNls String message) {
      delegate.debug(message);
    }

    @Override
    public void debug(@Nullable Throwable t) {
      delegate.debug(t);
    }

    @Override
    public void debug(@NonNls String message, @Nullable Throwable t) {
      delegate.debug(message, t);
    }

    @Override
    public void info(@NonNls String message) {
      delegate.info(message);
    }

    @Override
    public void info(@NonNls String message, @Nullable Throwable t) {
      delegate.info(message, t);
    }

    @Override
    public void warn(@NonNls String message, @Nullable Throwable t) {
      delegate.warn(message, t);
    }

    @Override
    public void error(@NonNls String message, @Nullable Throwable t, String @NotNull ... details) {
      delegate.error(message, t, details);
    }

    @Override
    public void trace(String message) {
      delegate.trace(message);
    }

    @Override
    public void trace(@Nullable Throwable t) {
      delegate.trace(t);
    }

    @Override
    public void setLevel(@NotNull Level level) {
      delegate.setLevel(level);
    }
  }
}