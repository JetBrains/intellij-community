// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.render.InlineBlockElementRenderer
import com.intellij.codeInsight.inline.completion.render.InlineSuffixRenderer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import java.awt.Graphics
import java.awt.Rectangle

data class InlineCompletionGrayTextElement(override val text: String) : InlineCompletionElement {

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  class Presentable(override val element: InlineCompletionElement) : InlineCompletionElement.Presentable {
    private var suffixInlay: Inlay<*>? = null
    private var blockInlay: Inlay<*>? = null

    override fun isVisible(): Boolean = suffixInlay != null || blockInlay != null

    override fun render(editor: Editor, offset: Int) {
      if (element.text.isEmpty()) return
      val lines = element.text.lines()
      renderSuffix(editor, lines, offset)
      if (lines.size > 1) {
        renderBlock(lines.drop(1), editor, offset)
      }
    }

    override fun getBounds(): Rectangle? {
      val bounds = suffixInlay?.bounds?.let { Rectangle(it) }
      blockInlay?.bounds?.let { bounds?.add(Rectangle(it)) }
      return bounds
    }

    override fun startOffset(): Int? = suffixInlay?.offset
    override fun endOffset(): Int? = suffixInlay?.offset

    override fun dispose() {
      blockInlay?.also(Disposer::dispose)
      blockInlay = null
      suffixInlay?.also(Disposer::dispose)
      suffixInlay = null
    }

    private fun renderSuffix(editor: Editor, lines: List<String>, offset: Int) {
      // The following is a hacky solution to the effect described in ML-977
      // ML-1781 Inline completion renders on the left to the caret after moving it
      editor.forceLeanLeft()

      val line = lines.first()
      if (line.isBlank()) {
        suffixInlay = editor.inlayModel.addInlineElement(editor.caretModel.offset, object : EditorCustomElementRenderer {
          override fun calcWidthInPixels(inlay: Inlay<*>) = 1
          override fun calcHeightInPixels(inlay: Inlay<*>) = 1
          override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {}
        })
        return
      }
      editor.inlayModel.execute(true) {
        val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, line)) ?: return@execute
        element.addActionAvailabilityHint(EditorActionAvailabilityHint(
          IdeActions.ACTION_INSERT_INLINE_COMPLETION,
          EditorActionAvailabilityHint.AvailabilityCondition.CaretOnStart,
        ))
        suffixInlay = element
      }
    }

    private fun renderBlock(
      lines: List<String>,
      editor: Editor,
      offset: Int
    ) {
      val element = editor.inlayModel.addBlockElement(
        offset, true, false, 1,
        InlineBlockElementRenderer(editor, lines)
      ) ?: return

      blockInlay = element
    }

    private fun Editor.forceLeanLeft() {
      val visualPosition = caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        caretModel.moveToVisualPosition(leftLeaningPosition)
      }
    }
  }
}
