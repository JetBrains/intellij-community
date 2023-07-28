// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging.jps;

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.Logger.Factory;
import com.intellij.openapi.diagnostic.RollingFileHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

@ApiStatus.Internal
public class JpsFileLoggerFactory implements Factory {
  private final RollingFileHandler myAppender;
  private final List<String> myCategoriesWithDebugLevel;

  public JpsFileLoggerFactory(Path logFile, String categoriesWithDebugLevel) {
    myCategoriesWithDebugLevel = categoriesWithDebugLevel.isEmpty() ? Collections.emptyList() : Arrays.asList(categoriesWithDebugLevel.split(","));
    myAppender = new RollingFileHandler(logFile, 20_000_000L, 10, true);
    myAppender.setFormatter(new IdeaLogRecordFormatter());
  }

  @NotNull
  @Override
  public Logger getLoggerInstance(@NotNull String category) {
    var logger = java.util.logging.Logger.getLogger(category);
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
