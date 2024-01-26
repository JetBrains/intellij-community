// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

class UnspecifiedLogger : JvmLogger() {
  override val loggerName: String = ""
  override val factoryName: String = ""
  override val methodName: String = ""
  override val classNamePattern: String = ""
  override val priority: Int = 100

  override fun isOnlyOnStartup() = true

  override fun toString(): String = UNSPECIFIED_LOGGER_NAME
}