@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger

// write to System.err as Bazel expects for worker log
internal class BazelLogger(private val category: String) : Logger() {
  private var level = LogLevel.DEBUG

  override fun isDebugEnabled(): Boolean = level >= LogLevel.DEBUG

  override fun isTraceEnabled(): Boolean = level >= LogLevel.TRACE

  override fun trace(message: String) {
    if (isTraceEnabled()) {
      System.err.println("TRACE[$category]: $message")
    }
  }

  override fun trace(t: Throwable?) {
    if (t != null && isTraceEnabled()) {
      System.err.println("TRACE[$category]: ")
      t.printStackTrace(System.err)
    }
  }

  override fun debug(message: String, t: Throwable?) {
    doPrint(LogLevel.DEBUG, message, t)
  }

  private fun doPrint(level: LogLevel, message: String, t: Throwable?) {
    if (this.level >= level) {
      return
    }

    var text = "${level.name}[$category]: $message"
    if (t != null) {
      ensureNotControlFlow(t)
      text += " " + t.stackTraceToString()
    }
    System.err.println(text)
  }

  override fun info(message: String, t: Throwable?) {
    doPrint(LogLevel.INFO, message, t)
  }

  override fun warn(message: String, t: Throwable?) {
    doPrint(LogLevel.WARNING, message, t)
  }

  override fun error(message: String, t: Throwable?, vararg details: String) {
    var t = t
    t = ensureNotControlFlow(t)
    System.err.println("ERROR: " + message + DefaultLogger.detailsToString(*details) + DefaultLogger.attachmentsToString(t))
    t?.printStackTrace(System.err)
    throw AssertionError(message, t)
  }

  override fun setLevel(level: LogLevel) {
    this.level = level
  }
}