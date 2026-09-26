// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.InMemoryHandler;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static com.intellij.openapi.diagnostic.InMemoryHandler.FAILED_BUILD_LOG_FILE_NAME_PREFIX;

@ApiStatus.Internal
public final class LogSetup {
  public static final String LOG_CONFIG_FILE_NAME = "build-log-jul.properties";
  private static final String LOG_FILE_NAME = "build.log";

  public static void initLoggers() {
    if (!Boolean.parseBoolean(System.getProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true"))) {
      return;
    }

    try {
      var logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      var configFile = logDir == null ? Path.of(LOG_CONFIG_FILE_NAME) : Path.of(logDir, LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      var logFilePath = logDir != null ? Path.of(logDir, LOG_FILE_NAME) : Path.of(LOG_FILE_NAME);

      JulLogger.clearHandlers();

      if (Boolean.getBoolean((GlobalOptions.USE_IN_MEMORY_FAILED_BUILD_LOGGER))) {
        var filter = acceptConfig(configFile);

        JulLogger.configureStandardLoggers(LogLevel.WARNING, true, logFilePath, true, false, null);

        var rootLogger = java.util.logging.Logger.getLogger("");

        var timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
        var failedBuildLogName = FAILED_BUILD_LOG_FILE_NAME_PREFIX + timestamp + ".log";
        var failedBuildLogPath = logDir != null ? Path.of(logDir, failedBuildLogName) : Path.of(failedBuildLogName);
        var inMemoryHandler = new InMemoryHandler(failedBuildLogPath);
        inMemoryHandler.setFormatter(new IdeaLogRecordFormatter());
        inMemoryHandler.setLevel(Level.FINEST);
        rootLogger.addHandler(inMemoryHandler);

        if (filter != null) {
          for (var handler : rootLogger.getHandlers()) {
            handler.setFilter(filter);
          }
        }
      }
      else {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(configFile))) {
          LogManager.getLogManager().readConfiguration(in);
        }
        JulLogger.configureStandardLoggers(LogLevel.WARNING, true, logFilePath, true, false, null);
      }
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(category -> new JulLogger(java.util.logging.Logger.getLogger(category)));
  }

  private static @Nullable Filter acceptConfig(Path configFile) throws IOException {
    String filterPrefix;

    var lines = Files.readAllLines(configFile);
    var debugLoggingEnabled = lines.contains("\\#org.jetbrains.jps.level=FINER");
    if (!debugLoggingEnabled) {
      lines = new ArrayList<>(lines);
      lines.add("\\#org.jetbrains.jps.level=FINER");
      filterPrefix = "#org.jetbrains.jps";
    }
    else {
      filterPrefix = null;
    }

    var configBytes = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    try (var updatedIn = new ByteArrayInputStream(configBytes)) {
      LogManager.getLogManager().readConfiguration(updatedIn);
    }

    if (filterPrefix == null) {
      return null;
    }
    else {
      return rec -> rec.getLevel().intValue() > Level.FINE.intValue() || !rec.getLoggerName().startsWith(filterPrefix);
    }
  }

  private static void ensureLogConfigExists(Path logConfig) throws IOException {
    if (!Files.exists(logConfig)) {
      Files.createDirectories(logConfig.getParent());
      try (var in = readDefaultLogConfig()) {
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
