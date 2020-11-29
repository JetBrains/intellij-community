// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used in org.jetbrains.intellij.build.impl.JpsCompilationRunner via jps-build-script-dependencies-bootstrap
 */
@SuppressWarnings("unused")
public class CompositeLogger extends Logger {
  private final Logger[] myLoggers;

  public CompositeLogger(Logger... loggers) {
    myLoggers = loggers;
  }

  @Override
  public boolean isDebugEnabled() {
    for (Logger logger : myLoggers) {
      if (logger.isDebugEnabled()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void debug(@NonNls String message) {
    for (Logger logger : myLoggers) {
      logger.debug(message);
    }
  }

  @Override
  public void debug(@Nullable Throwable t) {
    for (Logger logger : myLoggers) {
      logger.debug(t);
    }
  }

  @Override
  public void debug(@NonNls String message, @Nullable Throwable t) {
    for (Logger logger : myLoggers) {
      logger.debug(message, t);
    }
  }

  @Override
  public boolean isTraceEnabled() {
    for (Logger logger : myLoggers) {
      if (logger.isTraceEnabled()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void trace(String message) {
    for (Logger logger : myLoggers) {
      logger.trace(message);
    }
  }

  @Override
  public void trace(@Nullable Throwable t) {
    for (Logger logger : myLoggers) {
      logger.trace(t);
    }
  }

  @Override
  public void info(@NonNls String message) {
    for (Logger logger : myLoggers) {
      logger.info(message);
    }
  }

  @Override
  public void info(@NonNls String message, @Nullable Throwable t) {
    for (Logger logger : myLoggers) {
      logger.info(message, t);
    }
  }

  @Override
  public void warn(@NonNls String message, @Nullable Throwable t) {
    for (Logger logger : myLoggers) {
      logger.warn(message, t);
    }
  }

  @Override
  public void error(@NonNls String message, @Nullable Throwable t, @NonNls String @NotNull ... details) {
    for (Logger logger : myLoggers) {
      logger.error(message, t, details);
    }
  }

  @Override
  public void setLevel(@NotNull Level level) {
    for (Logger logger : myLoggers) {
      logger.setLevel(level);
    }
  }
}
