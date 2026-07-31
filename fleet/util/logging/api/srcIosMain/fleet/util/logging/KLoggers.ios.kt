// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.multiplatform.Actual
import kotlin.reflect.KClass
import platform.Foundation.NSLog

@Actual("getLoggerFactory")
internal fun getLoggerFactoryNative(): KLoggerFactory {
  return object : KLoggerFactory {
    override fun logger(owner: KClass<*>): KLogger {
      return getLogger(owner.simpleName ?: owner.toString()) { IosLoggingContext.map }
    }

    override fun logger(owner: Any): KLogger {
      return logger(owner::class)
    }

    override fun logger(name: String): KLogger {
      return getLogger(name) { IosLoggingContext.map }
    }

    override fun setLoggingContext(map: Map<String, String>?) {
      IosLoggingContext.map = map?.toMap().orEmpty()
    }

    override fun getLoggingContext(): Map<String, String> {
      return IosLoggingContext.map
    }
  }
}

@kotlin.native.concurrent.ThreadLocal
private object IosLoggingContext {
  var map: Map<String, String> = emptyMap()
}

private enum class IosConsoleLogLevel {
  ERROR, WARN, INFO, DEBUG, TRACE
}

private fun getLogger(name: String, contextProvider: () -> Map<String, String>): KLogger {
  return KLogger(object : BaseLogger {
    override val isTraceEnabled: Boolean = false
    override val isDebugEnabled: Boolean = true
    override val isInfoEnabled: Boolean = true
    override val isWarnEnabled: Boolean = true
    override val isErrorEnabled: Boolean = true

    override fun trace(message: Any?) {
      if (!isTraceEnabled) return
      log(IosConsoleLogLevel.TRACE, message)
    }

    override fun trace(t: Throwable?, message: Any?) {
      if (!isTraceEnabled) return
      log(IosConsoleLogLevel.TRACE, message, t)
    }

    override fun debug(message: Any?) {
      if (!isDebugEnabled) return
      log(IosConsoleLogLevel.DEBUG, message)
    }

    override fun debug(t: Throwable?, message: Any?) {
      if (!isDebugEnabled) return
      log(IosConsoleLogLevel.DEBUG, message, t)
    }

    override fun info(message: Any?) {
      if (!isInfoEnabled) return
      log(IosConsoleLogLevel.INFO, message)
    }

    override fun info(t: Throwable?, message: Any?) {
      if (!isInfoEnabled) return
      log(IosConsoleLogLevel.INFO, message, t)
    }

    override fun warn(message: Any?) {
      if (!isWarnEnabled) return
      log(IosConsoleLogLevel.WARN, message)
    }

    override fun warn(t: Throwable?, message: Any?) {
      if (!isWarnEnabled) return
      log(IosConsoleLogLevel.WARN, message, t)
    }

    override fun error(message: Any?) {
      if (!isErrorEnabled) return
      log(IosConsoleLogLevel.ERROR, message)
    }

    override fun error(t: Throwable?, message: Any?) {
      if (!isErrorEnabled) return
      log(IosConsoleLogLevel.ERROR, message, t)
    }

    private fun log(
      level: IosConsoleLogLevel,
      message: Any?,
      t: Throwable? = null,
    ) {
      val renderedMessage = message?.toString().orEmpty()
      val context = contextProvider()
      val logLine = buildString {
        append('[')
        append(level.name.padEnd(5))
        append(' ')
        append(name)
        append(']')
        context.entries.forEach { (key, value) ->
          append(" $key=$value")
        }
        if (renderedMessage.isNotEmpty()) {
          append(' ')
          append(renderedMessage)
        }
        t?.let {
          appendLine()
          it.message?.let { message ->
            append(message)
            appendLine()
          }
          append(it.stackTraceToString())
        }
      }
      // NSLog can hang forever if the log line is too long
      logLine.chunked(4000).forEach { chunk ->
        NSLog("%s", chunk)
      }
    }
  })
}
