// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Filter;
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
      String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      Path configFile = logDir == null ? Paths.get(LOG_CONFIG_FILE_NAME) : Paths.get(logDir, LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      Path logFilePath = logDir != null ? Paths.get(logDir, LOG_FILE_NAME) : Paths.get(LOG_FILE_NAME);

      Filter filter = null;
      Path failedBuildLogPath = null;
      if (Boolean.getBoolean((GlobalOptions.USE_IN_MEMORY_FAILED_BUILD_LOGGER))) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String failedBuildLogName = FAILED_BUILD_LOG_FILE_NAME_PREFIX + timestamp + ".log";
        failedBuildLogPath = logDir != null ? Paths.get(logDir, failedBuildLogName) : Paths.get(failedBuildLogName);

        JulLogger.clearHandlers();

        List<String> classesToFilter = acceptConfig(configFile);
        if (!classesToFilter.isEmpty()) {
          filter = JulLogger.createFilter(classesToFilter);
        }
      } else {
        JulLogger.clearHandlers();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(configFile))) {
          LogManager.getLogManager().readConfiguration(in);
        }
      }
      JulLogger.configureLogFileAndConsole(logFilePath, true, true, true, null, filter, failedBuildLogPath);
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(category -> new JulLogger(java.util.logging.Logger.getLogger(category)));
  }

  private static List<String> acceptConfig(Path configFile) throws IOException {
    List<String> classesToFilter = new LinkedList<>();
    try (InputStream in = new BufferedInputStream(Files.newInputStream(configFile))) {
      byte[] configBytes = in.readAllBytes();
      String configContent = new String(configBytes, StandardCharsets.UTF_8);

      List<String> lines = new ArrayList<>(Arrays.asList(configContent.split("\n")));
      boolean isDebugLoggingEnabled = false;

      for (String line : lines) {
        if (line.equals("\\#org.jetbrains.jps.level=FINER")) {
          isDebugLoggingEnabled = true;
          break;
        }
      }

      if (!isDebugLoggingEnabled) {
        lines.add("\\#org.jetbrains.jps.level=FINER");
        classesToFilter.add("#org.jetbrains.jps");
      }

      configContent = String.join("\n", lines);

      try (InputStream updatedIn = new ByteArrayInputStream(configContent.getBytes(StandardCharsets.UTF_8))) {
        LogManager.getLogManager().readConfiguration(updatedIn);
      }
    }
    return classesToFilter;
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
