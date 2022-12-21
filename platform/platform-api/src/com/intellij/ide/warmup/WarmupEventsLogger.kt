// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.openapi.util.NlsSafe

/**
 * Helps to pass diagnostic messages to the user of warm-up.
 */
interface WarmupEventsLogger {
  fun logError(message: @NlsSafe String)
  fun logMessage(verbosityLevel: Int, message: @NlsSafe String)
}