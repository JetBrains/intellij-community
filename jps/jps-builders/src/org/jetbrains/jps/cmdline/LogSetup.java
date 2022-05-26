// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.LogManager;

/**
 * @author Eugene Zhuravlev
 */
public final class LogSetup {
  public static final String LOG_CONFIG_FILE_NAME = "build-log-jul.properties";
  private static final String LOG_FILE_NAME = "build.log";

  public static void initLoggers() {
    if (!Boolean.parseBoolean(System.getProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true"))) {
      return;
    }

    try {
      String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      Path configFile = logDir == null ? Paths.get(LOG_CONFIG_FILE_NAME) : Paths.get(logDir, LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      Path logFilePath = logDir != null ? Paths.get(logDir, LOG_FILE_NAME) : Paths.get(LOG_FILE_NAME);
      JulLogger.clearHandlers();
      try (final InputStream in = Files.newInputStream(configFile)) {
        final BufferedInputStream bin = new BufferedInputStream(in);
        LogManager.getLogManager().readConfiguration(bin);
      }
      JulLogger.configureLogFileAndConsole(logFilePath, true, true, null);
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(category -> new JulLogger(java.util.logging.Logger.getLogger(category)));
  }

  private static void ensureLogConfigExists(@NotNull Path logConfig) throws IOException {
    if (!Files.exists(logConfig)) {
      Files.createDirectories(logConfig.getParent());
      try (InputStream in = readDefaultLogConfig()) {
        if (in != null) {
          Files.copy(in, logConfig);
        }
      }
    }
  }

  public static InputStream readDefaultLogConfig() {
    return LogSetup.class.getResourceAsStream("/defaultLogConfig.properties");
  }
}
