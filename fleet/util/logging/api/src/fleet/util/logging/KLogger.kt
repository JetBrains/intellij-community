// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

class KLogger(baseLogger: BaseLogger) : BaseLogger by baseLogger {
  inline fun trace(msg: () -> Any?) {
    if (isTraceEnabled) trace(msg())
  }

  inline fun debug(msg: () -> Any?) {
    if (isDebugEnabled) debug(msg())
  }

  inline fun info(msg: () -> Any?) {
    if (isInfoEnabled) info(msg())
  }

  inline fun warn(msg: () -> Any?) {
    if (isWarnEnabled) warn(msg())
  }

  inline fun error(msg: () -> Any?) {
    if (isErrorEnabled) error(msg())
  }

  inline fun trace(t: Throwable?, msg: () -> Any?) {
    if (isTraceEnabled) trace(t, msg())
  }

  inline fun debug(t: Throwable?, msg: () -> Any?) {
    if (isDebugEnabled) debug(t, msg())
  }

  inline fun info(t: Throwable?, msg: () -> Any?) {
    if (isInfoEnabled) info(t, msg())
  }

  inline fun warn(t: Throwable?, msg: () -> Any?) {
    if (isWarnEnabled) warn(t, msg())
  }

  inline fun error(t: Throwable?, msg: () -> Any?) {
    if (isErrorEnabled) error(t, msg())
  }
}