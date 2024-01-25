// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.openapi.extensions.ExtensionPointName

abstract class JvmLogger {
  abstract val loggerName: String
  protected abstract val factoryName: String
  protected abstract val methodName: String
  protected abstract val classNamePattern: String

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

    val EP_NAME = ExtensionPointName<JvmLogger>("com.intellij.jvm.logging")

    fun getAllLoggersNames(): List<String> {
      return EP_NAME.extensionList.map { it.toString() }
    }

    fun getLoggerByName(loggerName : String?) = EP_NAME.extensionList.find { it.toString() == loggerName }
  }
}
