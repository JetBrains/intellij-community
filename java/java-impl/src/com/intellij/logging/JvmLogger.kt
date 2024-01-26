// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.openapi.extensions.ExtensionPointName

abstract class JvmLogger(
  val loggerName: String,
  protected val factoryName: String,
  protected val methodName: String,
  private val classNamePattern: String,
  protected val priority: Int,
) {
  open fun isOnlyOnStartup() = false

  fun createLoggerFieldText(className: String): String {
    return "$loggerName ${LOGGER_IDENTIFIER} = ${factoryName}.$methodName(${String.format(classNamePattern, className)});"
  }

  companion object {
    const val LOGGER_IDENTIFIER = "LOGGER"
    const val UNSPECIFIED_LOGGER_NAME = "Unspecified"

    private val EP_NAME = ExtensionPointName<JvmLogger>("com.intellij.jvm.logging")

    fun getAllLoggersNames(isOnlyOnStartup: Boolean): List<String> {
      return getAllLoggers(isOnlyOnStartup).map { it.toString() }
    }

    fun getAllLoggers(isOnlyOnStartup: Boolean): List<JvmLogger> {
      return EP_NAME.extensionList.filter { if (!isOnlyOnStartup) !it.isOnlyOnStartup() else true }.sortedByDescending { it.priority }
    }

    fun getLoggerByName(loggerName: String?): JvmLogger? {
      if (loggerName == UNSPECIFIED_LOGGER_NAME) return null
      return EP_NAME.extensionList.find { it.toString() == loggerName }
    }
  }
}
