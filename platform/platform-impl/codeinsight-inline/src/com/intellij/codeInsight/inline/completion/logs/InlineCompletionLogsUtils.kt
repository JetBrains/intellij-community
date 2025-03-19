// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.RemDevAggregatorInlineCompletionProvider

internal object InlineCompletionLogsUtils {

  fun Class<out InlineCompletionProvider>.isLoggable(): Boolean {
    // Will be logged on the Backend side
    return !RemDevAggregatorInlineCompletionProvider::class.java.isAssignableFrom(this@isLoggable)
  }
}
