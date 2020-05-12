// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.log4j.PropertyConfigurator;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;

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
      final String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      final File configFile = logDir != null? new File(logDir, LOG_CONFIG_FILE_NAME) : new File(LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      String text = FileUtil.loadFile(configFile);
      final String logFile = logDir != null? new File(logDir, LOG_FILE_NAME).getAbsolutePath() : LOG_FILE_NAME;
      text = text.replace(LOG_FILE_MACRO, logFile.replace("\\", "\\\\"));
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

  private static void ensureLogConfigExists(final File logConfig) throws IOException {
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      try(InputStream in = readDefaultLogConfig()) {
        if (in != null) {
          try (FileOutputStream out = new FileOutputStream(logConfig)) {
            FileUtil.copy(in, out);
          }
        }
      }
    }
  }

  public static InputStream readDefaultLogConfig() {
    return LogSetup.class.getResourceAsStream("/defaultLogConfig.properties");
  }
}
