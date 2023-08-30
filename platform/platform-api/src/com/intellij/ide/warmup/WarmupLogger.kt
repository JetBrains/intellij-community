// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * The logger that is used to relay important information to the user.
 */
interface WarmupLogger {
  fun logInfo(message: String)
  fun logError(message: String, throwable: Throwable?)

  companion object {
    private val EP_NAME: ExtensionPointName<WarmupLogger> = ExtensionPointName("com.intellij.warmupLogger")

    fun message(message: String) {
      for (warmupLogger in EP_NAME.extensionList) {
        warmupLogger.logInfo(message)
      }
    }

    fun error(message: String, throwable: Throwable? = null) {
      for (warmupLogger in EP_NAME.extensionList) {
        warmupLogger.logError(message, throwable)
      }
    }
  }
}