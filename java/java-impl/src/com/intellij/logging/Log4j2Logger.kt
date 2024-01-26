// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class Log4j2Logger : JvmLogger() {
  override val loggerName: String = JavaLoggingUtils.LOG4J2
  override val factoryName: String = JavaLoggingUtils.LOG4J2_FACTORY
  override val methodName: String = "getLogger"
  override val classNamePattern: String = "%s.class"
  override val priority: Int = 30

  override fun toString(): String = "Log4j2"
}