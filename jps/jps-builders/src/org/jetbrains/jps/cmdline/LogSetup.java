// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Eugene Zhuravlev
 */
public final class LogSetup {
  public static final String LOG_CONFIG_FILE_NAME = "build-log.properties";
  private static final String LOG_FILE_NAME = "build.log";
  private static final String LOG_FILE_MACRO = "$LOG_FILE_PATH$";

  public static void initLoggers() {
    if (!Boolean.parseBoolean(System.getProperty(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "true"))) {
      return;
    }

    try {
      String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      Path configFile = logDir == null ? Paths.get(LOG_CONFIG_FILE_NAME) : Paths.get(logDir, LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      String logFile = logDir == null ? LOG_FILE_NAME : Paths.get(logDir, LOG_FILE_NAME).toAbsolutePath().toString();
      String text = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8)
        .replace(LOG_FILE_MACRO, logFile.replace("\\", "\\\\"));
      PropertyConfigurator.configure(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(category -> new Log4jBasedLogger(org.apache.log4j.Logger.getLogger(category)));
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
