package com.intellij.codeowners.scripts.common

import java.util.Date
import java.util.logging.ConsoleHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger


fun configureLogging() {
  val handler = ConsoleHandler().apply {
    level = Level.INFO
    formatter = OneLineJulFormatter()
  }

  Logger.getLogger("").apply {
    level = Level.INFO
    handlers.forEach { removeHandler(it) }
    addHandler(handler)
  }
}

private class OneLineJulFormatter : Formatter() {
  override fun format(record: LogRecord): String {
    val timestamp = Date(record.millis)
    val level = record.level.name
    val logger = record.loggerName
    val message = formatMessage(record)

    val throwable = record.thrown?.let {
      " | ${it::class.java.simpleName}: ${it.message}"
    } ?: ""

    return String.format(
      $$"%1$tF %1$tT %2$s %3$s - %4$s%5$s%n",
      timestamp,
      level,
      logger,
      message,
      throwable
    )
  }
}