// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.multiplatform.Actual
import kotlin.reflect.KClass

@Actual("getLoggerFactory")
internal fun getLoggerFactoryWasmJs(): KLoggerFactory {
  return object : KLoggerFactory {
    private val mdc = mutableMapOf<String, String>()

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
      mdc.clear()
      if (map != null) {
        mdc.putAll(map)
      }
    }

    override fun getLoggingContext(): Map<String, String>? {
      return mdc.toMap()
    }
  }
}

private enum class BrowserConsoleLogLevel {
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
      log(BrowserConsoleLogLevel.TRACE, message)
    }

    override fun trace(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.TRACE, message, t)
    }

    override fun debug(message: Any?) {
      log(BrowserConsoleLogLevel.DEBUG, message)
    }

    override fun debug(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.DEBUG, message, t)
    }

    override fun info(message: Any?) {
      log(BrowserConsoleLogLevel.INFO, message)
    }

    override fun info(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.INFO, message, t)
    }

    override fun warn(message: Any?) {
      log(BrowserConsoleLogLevel.WARN, message)
    }

    override fun warn(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.WARN, message, t)
      t?.printStackTrace()
    }

    override fun error(message: Any?) {
      log(BrowserConsoleLogLevel.ERROR, message)
    }

    override fun error(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.ERROR, message, t)
    }

    private fun log(level: BrowserConsoleLogLevel,
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
      when (level) {
        BrowserConsoleLogLevel.ERROR -> {
          consoleError(message)
        }
        BrowserConsoleLogLevel.WARN -> {
          consoleWarn(message)
        }
        BrowserConsoleLogLevel.INFO -> {
          consoleInfo(message)
        }
        BrowserConsoleLogLevel.DEBUG -> {
          consoleDebug(message)
        }
        BrowserConsoleLogLevel.TRACE -> {
          consoleTrace(message)
        }
      }
    }
  })
}

internal fun consoleTrace(message: String) {
  js("console.trace(message)")
}

internal fun consoleDebug(message: String) {
  js("console.debug(message)")
}

internal fun consoleInfo(message: String) {
  js("console.info(message)")
}

internal fun consoleWarn(message: String) {
  js("console.warn(message)")
}

internal fun consoleError(message: String) {
  js("console.error(message)")
}