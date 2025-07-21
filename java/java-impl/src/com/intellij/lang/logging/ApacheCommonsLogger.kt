// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

public class ApacheCommonsLogger : JvmLogger by JvmLoggerFieldDelegate(
  JavaLoggingUtils.COMMONS_LOGGING_FACTORY,
  "getLog",
  "%s.class",
  "Apache Commons Logging",
  JavaLoggingUtils.COMMONS_LOGGING,
  100,
) {
  override fun toString(): String = id
}