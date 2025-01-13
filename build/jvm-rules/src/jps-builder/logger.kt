@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.bazel.jvm.logging.LogEvent
import org.jetbrains.bazel.jvm.logging.LogWriter
import java.time.Instant

private val levelToNameMap = enumValues<LogLevel>().associateWith { if ((it == LogLevel.INFO)) null else it.name.lowercase() }

// klogging uses a separate coroutine for log even instead of adding to the channel
// write to System.err as Bazel expects for worker log
internal class BazelLogger(category: String, private val writer: LogWriter) : Logger() {
  private val maxLevel = LogLevel.INFO
  private val categoryContext = arrayOf<Any>("c", category.trimStart('#'))

  override fun isDebugEnabled(): Boolean = maxLevel >= LogLevel.DEBUG

  override fun isTraceEnabled(): Boolean = maxLevel >= LogLevel.TRACE

  override fun trace(message: String) {
    addEvent(LogLevel.TRACE, message, null)
  }

  override fun trace(t: Throwable?) {
    addEvent(LogLevel.TRACE, "", t)
  }

  override fun debug(message: String, t: Throwable?) {
    addEvent(LogLevel.DEBUG, message, t)
  }

  @Suppress("ReplaceGetOrSet")
  private fun addEvent(level: LogLevel, message: String, t: Throwable?) {
    if (level > maxLevel) {
      return
    }

    writer.log(LogEvent(
      timestamp = Instant.now(),
      message = message,
      context = categoryContext,
      level = levelToNameMap.get(level),
      exception = t,
    ))
  }

  override fun info(message: String, t: Throwable?) {
    addEvent(LogLevel.INFO, message, t)
  }

  override fun warn(message: String, t: Throwable?) {
    addEvent(LogLevel.WARNING, message, t)
  }

  override fun error(message: String?, t: Throwable?, vararg details: String) {
    addEvent(
      level = LogLevel.ERROR,
      message = (message ?: "") + DefaultLogger.detailsToString(*details) + DefaultLogger.attachmentsToString(t),
      t = t,
    )
  }

  override fun setLevel(level: LogLevel) {
    throw UnsupportedOperationException()
  }
}