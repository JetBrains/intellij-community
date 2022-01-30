// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gant;

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.RollingFileHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used in {@link org.jetbrains.intellij.build.impl.JpsCompilationRunner} via jps-build-script-dependencies-bootstrap
 */
@SuppressWarnings("unused")
public class Log4jFileLoggerFactory implements com.intellij.openapi.diagnostic.Logger.Factory {
  private final RollingFileHandler myAppender;
  private final List<String> myCategoriesWithDebugLevel;

  public Log4jFileLoggerFactory(File logFile, String categoriesWithDebugLevel) {
    myCategoriesWithDebugLevel = categoriesWithDebugLevel.isEmpty() ? Collections.emptyList() : Arrays.asList(categoriesWithDebugLevel.split(","));
    myAppender = new RollingFileHandler(logFile.toPath(), 20_000_000L, 10, true);
    myAppender.setFormatter(new IdeaLogRecordFormatter());
  }

  @NotNull
  @Override
  public com.intellij.openapi.diagnostic.Logger getLoggerInstance(@NotNull String category) {
    final Logger logger = Logger.getLogger(category);
    JulLogger.clearHandlers(logger);
    logger.addHandler(myAppender);
    logger.setUseParentHandlers(false);
    logger.setLevel(isDebugLevel(category) ? Level.FINE : Level.INFO);
    return new JulLogger(logger);
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
