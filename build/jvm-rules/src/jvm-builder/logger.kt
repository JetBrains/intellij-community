// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")
package org.jetbrains.bazel.jvm.worker

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span

// klogging uses a separate coroutine for log even instead of adding to the channel
// write to System.err as Bazel expects for worker log
internal class BazelLogger(category: String, private val span: Span) : Logger() {
  private val maxLevel = LogLevel.INFO
  private val sharedAttributes = Attributes.of(AttributeKey.stringKey("category"), category)

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

  private fun addEvent(level: LogLevel, message: String, t: Throwable?) {
    if (level > maxLevel) {
      return
    }

    span.addEvent(message, sharedAttributes)
    t?.let {
      span.recordException(it, sharedAttributes)
    }
  }

  override fun info(message: String?, t: Throwable?) {
    addEvent(LogLevel.INFO, message ?: "", t)
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