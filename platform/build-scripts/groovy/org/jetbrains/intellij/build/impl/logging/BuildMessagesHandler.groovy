// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages

import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

@CompileStatic
final class BuildMessagesHandler extends Handler {
  @NotNull final BuildMessages messages

  BuildMessagesHandler(@NotNull BuildMessages messages) {
    this.messages = messages
  }

  @Override
  void publish(LogRecord record) {
    def level = record.getLevel()
    String message = "[${record.loggerName}] ${record.message}"
    if (level.intValue() >= Level.SEVERE.intValue()) {
      def throwable = record.thrown
      if (throwable != null) {
        messages.error(message, throwable)
      }
      else {
        messages.error(message)
      }
      return
    }
    if (level.intValue() >= Level.WARNING.intValue()) {
      messages.warning(message)
      return
    }
    if (level.intValue() >= Level.INFO.intValue()) {
      messages.info(message)
      return
    }
    if (level.intValue() >= Level.FINE.intValue()) {
      messages.debug(message)
      return
    }
    messages.warning("Unsupported log4j level: $level")
    messages.info(message)
  }

  static void initLogging(BuildMessages messages) {
    Logger rootLogger = Logger.getLogger("")
    if (rootLogger.handlers.any {
      (it instanceof ConsoleHandler && it.formatter instanceof IdeaLogRecordFormatter)
        ||
      (it instanceof BuildMessagesHandler && (it as BuildMessagesHandler).messages == messages)
    }) {
      // already configured by this code or similar one
      return
    }

    for (Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler)
    }
    rootLogger.addHandler(new BuildMessagesHandler(messages))
  }

  @Override
  void flush() {
  }

  @Override
  void close(){
  }
}