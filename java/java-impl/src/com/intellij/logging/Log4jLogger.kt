// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class Log4jLogger : JvmLogger() {
  override val loggerName: String =   JavaLoggingUtils.LOG4J
  override val factoryName: String = JavaLoggingUtils.LOG4J_FACTORY
  override val methodName: String = "getLogger"
  override val classNamePattern: String = "%s.class"
  override val priority: Int = 5

  override fun toString(): String = "Log4j"
}