// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
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
  val widthInPixels: Int,
)

internal fun InlineCompletionRenderTextBlock.toVolumetric(editor: Editor, roundUp: Boolean): VolumetricInlineCompletionTextBlock {
  val width = InlineCompletionLineRenderer.widthOf(editor, this, roundUp = roundUp)
  return VolumetricInlineCompletionTextBlock(this, width)
}

/**
 * Text in an editor may have non-integer width. When we sum up integer widths of separate consecutive blocks, we end up with a value
 * that is less than the actual width. To avoid this, we can round up the width of each block. We'll end up with a greater value,
 * but it's okay for the purposes it is used.
 *
 * Also, we cannot easily get the actual double width,
 * because we require an instance of `Graphics` which is not accessible from some places.
 */
internal fun List<InlineCompletionRenderTextBlock>.toVolumetric(
  editor: Editor,
  roundUp: Boolean,
): List<VolumetricInlineCompletionTextBlock> = map { it.toVolumetric(editor, roundUp) }
