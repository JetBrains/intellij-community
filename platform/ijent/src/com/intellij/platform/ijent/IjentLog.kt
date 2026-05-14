// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger as JulLogger

/**
 * Thin logging wrapper tailored for IJent.
 *
 * Avoids a compile-time dependency on `com.intellij.openapi.diagnostic.Logger` (which lives in the heavy
 * `intellij.platform.util` module). When the IDE's diagnostic Logger is on the runtime classpath, IjentLog
 * reflectively routes every call through it — so logs go through the standard pipeline including the test
 * frameworks that hook `LoggedErrorProcessor`. When the platform Logger is absent (lightweight contexts),
 * IjentLog falls back to `java.util.logging` directly.
 */
@ApiStatus.Internal
class IjentLog private constructor(private val backend: Backend) {
  fun trace(message: () -> String) {
    if (backend.isTraceEnabled) backend.trace(message())
  }

  fun debug(message: () -> String) {
    if (backend.isDebugEnabled) backend.debug(message())
  }

  fun debug(throwable: Throwable, message: () -> String) {
    if (backend.isDebugEnabled) backend.debug(message(), throwable)
  }

  fun trace(message: String) {
    if (backend.isTraceEnabled) backend.trace(message)
  }

  fun debug(message: String) {
    if (backend.isDebugEnabled) backend.debug(message)
  }

  fun debug(message: String, throwable: Throwable) {
    if (backend.isDebugEnabled) backend.debug(message, throwable)
  }

  fun info(message: String) {
    backend.info(message, null)
  }

  fun info(message: String, throwable: Throwable) {
    backend.info(message, throwable)
  }

  fun info(throwable: Throwable) {
    backend.info(throwable.message.orEmpty(), throwable)
  }

  fun warn(message: String) {
    backend.warn(message, null)
  }

  fun warn(message: String, throwable: Throwable) {
    backend.warn(message, throwable)
  }

  fun warn(throwable: Throwable) {
    backend.warn(throwable.message.orEmpty(), throwable)
  }

  fun error(message: String) {
    backend.error(message, null)
  }

  fun error(message: String, throwable: Throwable) {
    backend.error(message, throwable)
  }

  fun error(throwable: Throwable) {
    backend.error(throwable.message.orEmpty(), throwable)
  }

  val isDebugEnabled: Boolean get() = backend.isDebugEnabled
  val isTraceEnabled: Boolean get() = backend.isTraceEnabled
  val name: String get() = backend.name

  companion object {
    @JvmStatic
    fun getInstance(name: String): IjentLog = IjentLog(createBackend(name))

    @JvmStatic
    fun getInstance(clazz: Class<*>): IjentLog = getInstance("#${clazz.name}")

    inline fun <reified T : Any> getInstance(): IjentLog = getInstance(T::class.java)
  }
}

private abstract class Backend {
  abstract val name: String
  abstract val isDebugEnabled: Boolean
  abstract val isTraceEnabled: Boolean
  abstract fun trace(message: String)
  abstract fun debug(message: String)
  abstract fun debug(message: String, throwable: Throwable)
  abstract fun info(message: String, throwable: Throwable?)
  abstract fun warn(message: String, throwable: Throwable?)
  abstract fun error(message: String, throwable: Throwable?)
}

/**
 * Resolves the platform Logger once, falls back to JUL when it is not on the classpath.
 * Mirrors the pattern of `com.intellij.openapi.diagnostic.LoggerRt`, but exposes the full API IjentLog needs
 * (debug, trace, level checks) which LoggerRt itself does not.
 */
private val PLATFORM_OPS: PlatformLoggerOps? = runCatching { PlatformLoggerOps() }.getOrNull()

private fun createBackend(name: String): Backend =
  PLATFORM_OPS?.let { PlatformBackend(name, it) } ?: JulBackend(name)

private class PlatformLoggerOps {
  private val loggerClass: Class<*> = Class.forName("com.intellij.openapi.diagnostic.Logger")
  val getInstance: Method = loggerClass.getMethod("getInstance", String::class.java).apply { isAccessible = true }
  val error: Method = loggerClass.getMethod("error", String::class.java, Throwable::class.java).apply { isAccessible = true }
  val warn: Method = loggerClass.getMethod("warn", String::class.java, Throwable::class.java).apply { isAccessible = true }
  val info: Method = loggerClass.getMethod("info", String::class.java, Throwable::class.java).apply { isAccessible = true }
  val debug: Method = loggerClass.getMethod("debug", String::class.java).apply { isAccessible = true }
  val debugWithThrowable: Method = loggerClass.getMethod("debug", String::class.java, Throwable::class.java).apply { isAccessible = true }
  val trace: Method = loggerClass.getMethod("trace", String::class.java).apply { isAccessible = true }
  val isDebug: Method = loggerClass.getMethod("isDebugEnabled").apply { isAccessible = true }
  val isTrace: Method = loggerClass.getMethod("isTraceEnabled").apply { isAccessible = true }
}

private class PlatformBackend(override val name: String, private val ops: PlatformLoggerOps) : Backend() {
  private val logger: Any = ops.getInstance.invoke(null, name)

  override val isDebugEnabled: Boolean
    get() = runCatching { ops.isDebug.invoke(logger) as Boolean }.getOrDefault(false)

  override val isTraceEnabled: Boolean
    get() = runCatching { ops.isTrace.invoke(logger) as Boolean }.getOrDefault(false)

  override fun trace(message: String) {
    runCatching { ops.trace.invoke(logger, message) }
  }

  override fun debug(message: String) {
    runCatching { ops.debug.invoke(logger, message) }
  }

  override fun debug(message: String, throwable: Throwable) {
    runCatching { ops.debugWithThrowable.invoke(logger, message, throwable) }
  }

  override fun info(message: String, throwable: Throwable?) {
    runCatching { ops.info.invoke(logger, message, throwable) }
  }

  override fun warn(message: String, throwable: Throwable?) {
    runCatching { ops.warn.invoke(logger, message, throwable) }
  }

  override fun error(message: String, throwable: Throwable?) {
    runCatching { ops.error.invoke(logger, message, throwable) }
  }
}

private class JulBackend(override val name: String) : Backend() {
  private val jul: JulLogger = JulLogger.getLogger(name)

  override val isDebugEnabled: Boolean get() = jul.isLoggable(Level.FINE)
  override val isTraceEnabled: Boolean get() = jul.isLoggable(Level.FINER)

  override fun trace(message: String) {
    jul.log(Level.FINER, message)
  }

  override fun debug(message: String) {
    jul.log(Level.FINE, message)
  }

  override fun debug(message: String, throwable: Throwable) {
    jul.log(Level.FINE, message, throwable)
  }

  override fun info(message: String, throwable: Throwable?) {
    if (throwable != null) jul.log(Level.INFO, message, throwable) else jul.log(Level.INFO, message)
  }

  override fun warn(message: String, throwable: Throwable?) {
    if (throwable != null) jul.log(Level.WARNING, message, throwable) else jul.log(Level.WARNING, message)
  }

  override fun error(message: String, throwable: Throwable?) {
    if (throwable != null) jul.log(Level.SEVERE, message, throwable) else jul.log(Level.SEVERE, message)
  }
}
