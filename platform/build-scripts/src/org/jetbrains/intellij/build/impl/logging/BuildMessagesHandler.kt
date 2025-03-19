// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import java.util.logging.*

internal class BuildMessagesHandler(private val messages: BuildMessagesImpl) : Handler() {
  companion object {
    fun initLogging(messages: BuildMessagesImpl) {
      val rootLogger = Logger.getLogger("")
      if (rootLogger.handlers.any { (it is ConsoleHandler && it.formatter is IdeaLogRecordFormatter) ||
                                    (it is BuildMessagesHandler && it.messages == messages) }) {
        // already configured by this code or similar one
        return
      }

      for (handler in rootLogger.handlers) {
        rootLogger.removeHandler(handler)
      }
      rootLogger.addHandler(BuildMessagesHandler(messages))
    }
  }

  override fun publish(record: LogRecord) {
    val level = record.level
    val message = "[${record.loggerName}] ${record.message}"
    if (level.intValue() >= Level.SEVERE.intValue()) {
      val throwable = record.thrown
      if (throwable == null) {
        messages.error(message)
      }
      else {
        messages.error(message, throwable)
      }
      return
    }

    when {
      level.intValue() >= Level.WARNING.intValue() -> {
        messages.warning(message)
        return
      }
      level.intValue() >= Level.INFO.intValue() -> {
        messages.info(message)
        return
      }
      level.intValue() >= Level.FINE.intValue() -> {
        messages.debug(message)
        return
      }
      else -> {
        messages.warning("Unsupported log4j level: $level")
        messages.info(message)
      }
    }
  }

  override fun flush() {
  }

  override fun close() {
  }
}