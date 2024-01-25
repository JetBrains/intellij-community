// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class JavaUtilLogger : JvmLogger() {
  override val loggerName: String = JavaLoggingUtils.JAVA_LOGGING
  override val factoryName: String = JavaLoggingUtils.JAVA_LOGGING_FACTORY
  override val methodName: String = "getLogger"
  override val classNamePattern: String = "%s.class.getName()"

  override fun toString(): String = "java.util.logging"
}