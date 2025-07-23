// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.openapi.editor.colors.CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES
import com.intellij.openapi.editor.colors.CodeInsightColors.HYPERLINK_ATTRIBUTES
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun EditorColorsScheme.tryRecoverConsoleTextAttributesKey(attributes: TextAttributes?): TextAttributesKey? {
  return when (attributes) {
    null -> null
    getAttributes(HYPERLINK_ATTRIBUTES) -> HYPERLINK_ATTRIBUTES
    getAttributes(FOLLOWED_HYPERLINK_ATTRIBUTES) -> FOLLOWED_HYPERLINK_ATTRIBUTES
    getAttributes(ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY) -> ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY) -> ConsoleViewContentType.LOG_DEBUG_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_VERBOSE_OUTPUT_KEY) -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_INFO_OUTPUT_KEY) -> ConsoleViewContentType.LOG_INFO_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY) -> ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY) -> ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.LOG_EXPIRED_ENTRY) -> ConsoleViewContentType.LOG_EXPIRED_ENTRY
    getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY) -> ConsoleViewContentType.NORMAL_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.ERROR_OUTPUT_KEY) -> ConsoleViewContentType.ERROR_OUTPUT_KEY
    getAttributes(ConsoleViewContentType.USER_INPUT_KEY) -> ConsoleViewContentType.USER_INPUT_KEY
    getAttributes(ConsoleViewContentType.SYSTEM_OUTPUT_KEY) -> ConsoleViewContentType.SYSTEM_OUTPUT_KEY
    else -> null
  }
}
