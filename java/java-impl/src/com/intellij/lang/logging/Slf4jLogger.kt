// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

public class Slf4jLogger : JvmLogger by JvmLoggerFieldDelegate(
  JavaLoggingUtils.SLF4J_FACTORY,
  "getLogger",
  "%s.class",
  "Slf4j",
  JavaLoggingUtils.SLF4J,
  300,
) {
  override fun toString(): String = id
}