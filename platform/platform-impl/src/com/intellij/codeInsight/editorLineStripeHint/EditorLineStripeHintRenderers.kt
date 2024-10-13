// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorLineStripeHint

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Rectangle

sealed class EditorLineStripeInlayRenderer(text: String) : HintRenderer(text) {
  // exposes protected method in HintRenderer to the current package
  internal fun internalGetTextAttributes(editor: Editor): TextAttributes? = getTextAttributes(editor)
}

class EditorLineStripeButtonRenderer(text: String) : EditorLineStripeInlayRenderer(text) {
  override fun toString(): String {
    return "<|$text|>"
  }
}

class EditorLineStripeTextRenderer(text: @Nls String) : EditorLineStripeInlayRenderer(text), InputHandler {
  override fun getTextAttributes(editor: Editor): TextAttributes? {
    return super.getTextAttributes(editor)?.clearEffects(editor)
  }

  private fun TextAttributes.clearEffects(editor: Editor): TextAttributes = clone().apply {
    effectType = null
    effectColor = null
    backgroundColor = null
    foregroundColor = InlineCompletionFontUtils.color(editor)
    setAdditionalEffects(emptyMap())
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    // shift because we want two independent inlays to looks related
    val shiftedRectangle = Rectangle(r.x - 5, r.y, r.width, r.height)
    super.paint(inlay, g, shiftedRectangle, textAttributes)
  }

  override fun toString(): String {
    return "<#$text#>"
  }
}