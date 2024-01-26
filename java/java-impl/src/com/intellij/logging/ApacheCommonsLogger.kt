// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class ApacheCommonsLogger : JvmLogger() {
  override val loggerName: String = JavaLoggingUtils.COMMONS_LOGGING

  override val factoryName: String = JavaLoggingUtils.COMMONS_LOGGING_FACTORY

  override val methodName: String = "getLog"

  override val classNamePattern: String = "%s.class"

  override val priority: Int = 20

  override fun toString(): String = "Apache Commons Logging"
}