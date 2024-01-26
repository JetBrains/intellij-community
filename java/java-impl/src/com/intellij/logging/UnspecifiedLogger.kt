// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

class UnspecifiedLogger : JvmLogger(
  "",
  "",
  "",
  "",
  100
) {
  override fun isOnlyOnStartup() = true

  override fun toString(): String = UNSPECIFIED_LOGGER_NAME
}