// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class Log4jLogger : JvmLogger(
  JavaLoggingUtils.LOG4J,
  JavaLoggingUtils.LOG4J_FACTORY,
  "getLogger",
  "%s.class",
  5,
) {
  override fun toString(): String = "Log4j"
}