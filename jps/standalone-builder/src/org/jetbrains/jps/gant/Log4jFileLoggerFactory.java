/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.gant;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class Log4jFileLoggerFactory implements com.intellij.openapi.diagnostic.Logger.Factory {
  private final RollingFileAppender myAppender;
  private final List<String> myCategoriesWithDebugLevel;

  public Log4jFileLoggerFactory(File logFile, String categoriesWithDebugLevel) throws IOException {
    myCategoriesWithDebugLevel = categoriesWithDebugLevel.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(categoriesWithDebugLevel.split(","));
    PatternLayout pattern = new PatternLayout("%d [%7r] %6p - %30.30c - %m\n");
    myAppender = new RollingFileAppender(pattern, logFile.getAbsolutePath());
    myAppender.setMaxFileSize("20MB");
    myAppender.setMaxBackupIndex(10);
  }

  @Override
  public com.intellij.openapi.diagnostic.Logger getLoggerInstance(String category) {
    final Logger logger = Logger.getLogger(category);
    logger.addAppender(myAppender);
    logger.setLevel(isDebugLevel(category) ? Level.DEBUG : Level.INFO);
    return new com.intellij.openapi.diagnostic.Logger() {
      @Override
      public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
      }

      @Override
      public void debug(@NonNls String message) {
        logger.debug(message);
      }

      @Override
      public void debug(@Nullable Throwable t) {
        logger.debug(t);
      }

      @Override
      public void debug(@NonNls String message, @Nullable Throwable t) {
        logger.debug(message, t);
      }

      @Override
      public void info(@NonNls String message) {
        logger.info(message);
      }

      @Override
      public void info(@NonNls String message, @Nullable Throwable t) {
        logger.info(message, t);
      }

      @Override
      public void warn(@NonNls String message, @Nullable Throwable t) {
        logger.warn(message, t);
      }

      @Override
      public void error(@NonNls String message, @Nullable Throwable t, @NonNls @NotNull String... details) {
        logger.error(message, t);
      }

      @Override
      public void setLevel(Level level) {
        logger.setLevel(level);
      }
    };
  }

  private boolean isDebugLevel(String category) {
    for (String debug : myCategoriesWithDebugLevel) {
      if (category.startsWith(debug)) {
        return true;
      }
    }
    return false;
  }
}
