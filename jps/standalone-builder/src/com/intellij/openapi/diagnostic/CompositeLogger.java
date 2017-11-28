/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class CompositeLogger extends Logger {
  private Logger[] myLoggers;

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
  public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
    for (Logger logger : myLoggers) {
      logger.error(message, t, details);
    }
  }

  @Override
  public void setLevel(Level level) {
    for (Logger logger : myLoggers) {
      logger.setLevel(level);
    }
  }
}
