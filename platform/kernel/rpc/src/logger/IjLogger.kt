// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.logger

import com.intellij.openapi.diagnostic.Logger
import fleet.util.logging.BaseLogger

internal class IjLogger(private val logger: Logger) : BaseLogger {

  override val isTraceEnabled: Boolean
    get() = logger.isTraceEnabled

  override val isDebugEnabled: Boolean
    get() = logger.isDebugEnabled

  override val isInfoEnabled: Boolean
    get() = true

  override val isWarnEnabled: Boolean
    get() = true

  override val isErrorEnabled: Boolean
    get() = true

  override fun trace(message: Any?) {
    logger.trace(message?.toString() ?: "null")
  }

  override fun trace(t: Throwable?, message: Any?) {
    logger.trace(message?.toString() ?: "null")
    if (t != null) {
      logger.trace(t)
    }
  }

  override fun debug(message: Any?) {
    logger.debug(message?.toString() ?: "null")
  }

  override fun debug(t: Throwable?, message: Any?) {
    logger.debug(message?.toString() ?: "null", t)
  }

  override fun info(message: Any?) {
    logger.info(message?.toString() ?: "null")
  }

  override fun info(t: Throwable?, message: Any?) {
    logger.info(message?.toString() ?: "null", t)
  }

  override fun warn(message: Any?) {
    logger.warn(message?.toString() ?: "null")
  }

  override fun warn(t: Throwable?, message: Any?) {
    logger.warn(message?.toString() ?: "null", t)
  }

  override fun error(message: Any?) {
    logger.error(message.toString())
  }

  override fun error(t: Throwable?, message: Any?) {
    logger.error(message?.toString() ?: "null", t)
  }
}
