// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.multiplatform.Actual
import platform.Foundation.NSLog
import kotlin.concurrent.AtomicReference
import kotlin.reflect.KClass


@Actual("getLoggerFactory")
internal fun getLoggerFactoryNative(): KLoggerFactory {
  return object : KLoggerFactory {
    private val mdc = AtomicReference(emptyMap<String, String>())

    override fun logger(owner: KClass<*>): KLogger {
      return getLogger(owner.simpleName ?: owner.toString())
    }

    override fun logger(owner: Any): KLogger {
      return logger(owner::class)
    }

    override fun logger(name: String): KLogger {
      return getLogger(name)
    }

    override fun setLoggingContext(map: Map<String, String>?) {
      mdc.value = map ?: emptyMap()
    }

    override fun getLoggingContext(): Map<String, String> {
      return mdc.value
    }
  }
}

private enum class IosConsoleLogLevel {
  ERROR, WARN, INFO, DEBUG, TRACE
}

private fun getLogger(name: String): KLogger {
  return KLogger(object : BaseLogger {
    override val isTraceEnabled: Boolean = false
    override val isDebugEnabled: Boolean = true
    override val isInfoEnabled: Boolean = true
    override val isWarnEnabled: Boolean = true
    override val isErrorEnabled: Boolean = true

    override fun trace(message: Any?) {
      log(IosConsoleLogLevel.TRACE, message)
    }

    override fun trace(t: Throwable?, message: Any?) {
      log(IosConsoleLogLevel.TRACE, message, t)
    }

    override fun debug(message: Any?) {
      log(IosConsoleLogLevel.DEBUG, message)
    }

    override fun debug(t: Throwable?, message: Any?) {
      log(IosConsoleLogLevel.DEBUG, message, t)
    }

    override fun info(message: Any?) {
      log(IosConsoleLogLevel.INFO, message)
    }

    override fun info(t: Throwable?, message: Any?) {
      log(IosConsoleLogLevel.INFO, message, t)
    }

    override fun warn(message: Any?) {
      log(IosConsoleLogLevel.WARN, message)
    }

    override fun warn(t: Throwable?, message: Any?) {
      log(IosConsoleLogLevel.WARN, message, t)
      t?.printStackTrace()
    }

    override fun error(message: Any?) {
      log(IosConsoleLogLevel.ERROR, message)
    }

    override fun error(t: Throwable?, message: Any?) {
      log(IosConsoleLogLevel.ERROR, message, t)
    }

    private fun log(level: IosConsoleLogLevel,
                    message: Any?,
                    t: Throwable? = null) {
      val message = buildString {
        append('[')
        append(level.name.padEnd(5))
        append(' ')
        append(name)
        append(']')
        append(' ')
        append(message.toString())
        t?.let {
          appendLine()
          it.message?.let { message ->
            append(message)
            appendLine()
          }
          append(it.stackTraceToString())
        }
      }
      // otherwise the logger hangs infinitely on long strings
      message.chunked(5000).forEach { message ->
        when (level) {
          IosConsoleLogLevel.ERROR -> {
            consoleError(message)
          }
          IosConsoleLogLevel.WARN -> {
            consoleWarn(message)
          }
          IosConsoleLogLevel.INFO -> {
            consoleInfo(message)
          }
          IosConsoleLogLevel.DEBUG -> {
            consoleDebug(message)
          }
          IosConsoleLogLevel.TRACE -> {
            consoleTrace(message)
          }
        }
      }
    }
  })
}

// TODO: I'm pretty sure this can misbehave. The first parameter is not a string, it's a format string.
// It then accepts parameters to log. I just had a situation where the log was misinterpreted and some random
// piece of memory was printed into the log. This would randomly segfault. The only reason I'm not fixing it now
// properly.
internal fun consoleTrace(message: String) {
  NSLog("[TRACE] $message")
}

internal fun consoleDebug(message: String) {
  NSLog("[DEBUG] $message")
}

internal fun consoleInfo(message: String) {
  NSLog("[INFO] $message")
}

internal fun consoleWarn(message: String) {
  NSLog("[WARN] $message")
}

internal fun consoleError(message: String) {
  NSLog("[ERROR] $message")
}