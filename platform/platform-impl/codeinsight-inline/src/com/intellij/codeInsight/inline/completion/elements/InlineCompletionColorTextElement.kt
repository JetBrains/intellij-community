// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * Draws text using IntelliJ inlays with respect to provided [Color].
 *
 * It uses [InlineCompletionFontUtils.attributes] with changed [TextAttributes.getForegroundColor].
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class InlineCompletionColorTextElement(
  override val text: String,
  val getColor: (Editor) -> Color
) : InlineCompletionTextElement(text, getColor.toGetAttributes()) {

  constructor(text: String, color: Color) : this(text, { color })

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this, getColor)

  open class Presentable(
    override val element: InlineCompletionElement,
    val getColor: (Editor) -> Color
  ) : InlineCompletionTextElement.Presentable(element, getColor.toGetAttributes())

  companion object {
    private fun ((Editor) -> Color).toGetAttributes(): (Editor) -> TextAttributes = { editor ->
      val color = this(editor)
      InlineCompletionFontUtils.attributes(editor).clone().apply {
        foregroundColor = color
      }
    }
  }
}
