// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging.slf4j

import fleet.util.logging.BaseLogger
import org.slf4j.Logger

internal class JVMLogger(private val sl4jLogger: Logger) : BaseLogger {
  override val isTraceEnabled: Boolean get() = sl4jLogger.isTraceEnabled
  override val isDebugEnabled: Boolean get() = sl4jLogger.isDebugEnabled
  override val isInfoEnabled: Boolean get() = sl4jLogger.isInfoEnabled
  override val isWarnEnabled: Boolean get() = sl4jLogger.isWarnEnabled
  override val isErrorEnabled: Boolean get() = sl4jLogger.isErrorEnabled

  override fun trace(message: Any?) {
    sl4jLogger.trace(message.toString())
  }

  override fun debug(message: Any?) {
    sl4jLogger.debug(message.toString())
  }

  override fun info(message: Any?) {
    sl4jLogger.info(message.toString())
  }

  override fun warn(message: Any?) {
    sl4jLogger.warn(message.toString())
  }

  override fun error(message: Any?) {
    sl4jLogger.error(message.toString())
  }

  override fun trace(t: Throwable?, message: Any?) {
    sl4jLogger.trace(message.toString(), t)
  }

  override fun debug(t: Throwable?, message: Any?) {
    sl4jLogger.debug(message.toString(), t)
  }

  override fun info(t: Throwable?, message: Any?) {
    sl4jLogger.info(message.toString(), t)
  }

  override fun warn(t: Throwable?, message: Any?) {
    sl4jLogger.warn(message.toString(), t)
  }

  override fun error(t: Throwable?, message: Any?) {
    sl4jLogger.error(message.toString(), t)
  }
}
