/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 16-Jul-15
 */
public class LogSetup {

  private static final String LOG_CONFIG_FILE_NAME = "build-log.properties";
  private static final String LOG_FILE_NAME = "build.log";
  private static final String DEFAULT_LOGGER_CONFIG = "defaultLogConfig.properties";
  private static final String LOG_FILE_MACRO = "$LOG_FILE_PATH$";

  public static void initLoggers() {
    try {
      final String logDir = System.getProperty(GlobalOptions.LOG_DIR_OPTION, null);
      final File configFile = logDir != null? new File(logDir, LOG_CONFIG_FILE_NAME) : new File(LOG_CONFIG_FILE_NAME);
      ensureLogConfigExists(configFile);
      String text = FileUtil.loadFile(configFile);
      final String logFile = logDir != null? new File(logDir, LOG_FILE_NAME).getAbsolutePath() : LOG_FILE_NAME;
      text = StringUtil.replace(text, LOG_FILE_MACRO, StringUtil.replace(logFile, "\\", "\\\\"));
      PropertyConfigurator.configure(new ByteArrayInputStream(text.getBytes("UTF-8")));
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Failed to configure logging: ");
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
    }

    Logger.setFactory(MyLoggerFactory.class);
  }

  private static void ensureLogConfigExists(final File logConfig) throws IOException {
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      final InputStream in = LogSetup.class.getResourceAsStream("/" + DEFAULT_LOGGER_CONFIG);
      if (in != null) {
        try {
          final FileOutputStream out = new FileOutputStream(logConfig);
          try {
            FileUtil.copy(in, out);
          }
          finally {
            out.close();
          }
        }
        finally {
          in.close();
        }
      }
    }
  }

  private static class MyLoggerFactory implements Logger.Factory {
    @Override
    public Logger getLoggerInstance(String category) {
      final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(category);

      return new Logger() {
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
          logger.debug("", t);
        }

        @Override
        public void debug(@NonNls String message, @Nullable Throwable t) {
          logger.debug(message, t);
        }

        @Override
        public void error(@NonNls String message, @Nullable Throwable t, @NotNull @NonNls String... details) {
          logger.error(message, t);
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
        public void setLevel(Level level) {
          logger.setLevel(level);
        }
      };
    }
  }
}
