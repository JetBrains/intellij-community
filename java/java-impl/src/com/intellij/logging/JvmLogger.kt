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

  companion object {
    const val LOGGER_IDENTIFIER = "LOGGER"

    val EP_NAME = ExtensionPointName<JvmLogger>("com.intellij.jvm.logging")
  }
}
