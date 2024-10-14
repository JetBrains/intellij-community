// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.hint

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Internal
abstract class InlineShortcutHintRendererBase(text: String?) : HintRenderer(text) {

  protected abstract fun paintIfEnabled(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes)

  protected abstract fun isEnabled(editor: Editor): Boolean

  final override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    if (isEnabled(inlay.editor)) {
      paintIfEnabled(inlay, g, r, textAttributes)
    }
  }

  override fun getTextAttributes(editor: Editor): TextAttributes? {
    return super.getTextAttributes(editor)?.clearEffects()
  }

  protected fun TextAttributes.clearEffects(): TextAttributes = clone().apply {
    effectType = null
    effectColor = null
    setAdditionalEffects(emptyMap())
  }

  protected fun paintLabel(
    g: Graphics,
    editor: EditorImpl,
    originalRectangle: Rectangle,
    text: String?,
    textAttributes: TextAttributes,
    transformAttributes: (TextAttributes) -> TextAttributes = { it }
  ) {
    val attributes = (getTextAttributes(editor) ?: textAttributes).clone().apply {
      backgroundColor = null
      foregroundColor = InlineCompletionFontUtils.color(editor)
    }
    // 'r.x - 2' is from HintRenderer
    val shiftedRectangle = Rectangle(originalRectangle.x - 2, originalRectangle.y, originalRectangle.width, originalRectangle.height)
    paintHint(
      g,
      editor,
      shiftedRectangle,
      text,
      transformAttributes(attributes).clearEffects(),
      transformAttributes(textAttributes).clearEffects(),
      null
    )
  }

  protected fun paintHint(
    inlay: Inlay<*>,
    g: Graphics,
    r: Rectangle,
    textAttributes: TextAttributes,
  ) {
    super.paint(inlay, g, r, textAttributes)
  }

  companion object {
    @JvmStatic
    fun isAvailableForLine(editor: Editor, lineNumber: Int): Boolean {
      // Debugger may suggest something on the right, so inlays will mix up
      val inlaysAfterLine = editor.inlayModel.getAfterLineEndElementsForLogicalLine(lineNumber)
      return inlaysAfterLine.all { it.renderer is InlineShortcutHintRendererBase }
    }
  }
}
