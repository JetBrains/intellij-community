// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionRenderTextBlock(val text: String, val attributes: TextAttributes) {
  init {
    require(text.none { it == '\n' || it == '\r' })
  }
}
