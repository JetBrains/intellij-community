// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

public class Log4jLogger : JvmLogger by JvmLoggerFieldDelegate(
  JavaLoggingUtils.LOG4J_FACTORY,
  "getLogger",
  "%s.class",
  "Log4j",
  JavaLoggingUtils.LOG4J,
  0,
) {
  override fun toString(): String = id
}