// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.openapi.extensions.ExtensionPointName

abstract class JvmLogger {
  abstract val loggerName: String
  protected abstract val factoryName: String
  protected abstract val methodName: String
  protected abstract val classNamePattern: String
  protected abstract val priority: Int

  open fun isOnlyOnStartup() = false

  fun createLoggerFieldText(className: String): String {
    return "$loggerName ${LOGGER_IDENTIFIER} = ${factoryName}.$methodName(${String.format(classNamePattern, className)});"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JvmLogger

    if (loggerName != other.loggerName) return false
    if (factoryName != other.factoryName) return false
    if (methodName != other.methodName) return false
    if (classNamePattern != other.classNamePattern) return false

    return true
  }

  override fun hashCode(): Int {
    var result = loggerName.hashCode()
    result = 31 * result + factoryName.hashCode()
    result = 31 * result + methodName.hashCode()
    result = 31 * result + classNamePattern.hashCode()
    return result
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
