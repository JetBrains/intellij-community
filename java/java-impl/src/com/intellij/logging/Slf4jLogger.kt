// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.siyeh.ig.psiutils.JavaLoggingUtils

class Slf4jLogger : JvmLogger(
  JavaLoggingUtils.SLF4J,
  JavaLoggingUtils.SLF4J_FACTORY,
  "getLogger",
  "%s.class",
  40,
) {
  override fun toString(): String = "Slf4j"
}