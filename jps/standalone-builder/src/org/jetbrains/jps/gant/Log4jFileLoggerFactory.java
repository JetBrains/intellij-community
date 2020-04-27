// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.gant;

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Used in org.jetbrains.intellij.build.impl.JpsCompilationData via jps-build-script-dependencies-bootstrap
 */
@SuppressWarnings("unused")
public class Log4jFileLoggerFactory implements com.intellij.openapi.diagnostic.Logger.Factory {
  private final RollingFileAppender myAppender;
  private final List<String> myCategoriesWithDebugLevel;

  public Log4jFileLoggerFactory(File logFile, String categoriesWithDebugLevel) throws IOException {
    myCategoriesWithDebugLevel = categoriesWithDebugLevel.isEmpty() ? Collections.emptyList() : Arrays.asList(categoriesWithDebugLevel.split(","));
    PatternLayout pattern = new PatternLayout("%d [%7r] %6p - %30.30c - %m\n");
    myAppender = new RollingFileAppender(pattern, logFile.getAbsolutePath());
    myAppender.setMaxFileSize("20MB");
    myAppender.setMaxBackupIndex(10);
  }

  @NotNull
  @Override
  public com.intellij.openapi.diagnostic.Logger getLoggerInstance(@NotNull String category) {
    final Logger logger = Logger.getLogger(category);
    logger.addAppender(myAppender);
    logger.setLevel(isDebugLevel(category) ? Level.DEBUG : Level.INFO);
    return new Log4jBasedLogger(logger);
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
