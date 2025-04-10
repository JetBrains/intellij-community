// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a block to be rendered **on one line**. Passing a multiline text results into an exception.
 */
@ApiStatus.Internal
data class InlineCompletionRenderTextBlock(
  val text: String,
  val attributes: TextAttributes,
  val data: UserDataHolderBase,
) {

  constructor(text: String, attributes: TextAttributes) : this(text, attributes, UserDataHolderBase())

  init {
    require(text.none { it == '\n' || it == '\r' })
  }
}

internal data class VolumetricInlineCompletionTextBlock(
  val block: InlineCompletionRenderTextBlock,
  val widthInPixels: Double,
)
