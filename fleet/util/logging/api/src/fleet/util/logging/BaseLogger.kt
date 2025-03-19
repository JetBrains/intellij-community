// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

interface BaseLogger {
  val isTraceEnabled: Boolean
  val isDebugEnabled: Boolean
  val isInfoEnabled: Boolean
  val isWarnEnabled: Boolean
  val isErrorEnabled: Boolean

  fun trace(message: Any?)
  fun debug(message: Any?)
  fun info(message: Any?)
  fun warn(message: Any?)
  fun error(message: Any?)

  fun trace(t: Throwable?, message: Any? = "")
  fun debug(t: Throwable?, message: Any? = "")
  fun info(t: Throwable?, message: Any? = "")
  fun warn(t: Throwable?, message: Any? = "")
  fun error(t: Throwable?, message: Any? = "")
}
