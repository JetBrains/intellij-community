// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.render.InlineBlockElementRenderer
import com.intellij.codeInsight.inline.completion.render.InlineCompletionBlock
import com.intellij.codeInsight.inline.completion.render.InlineSuffixRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Experimental
data class InlineCompletionGrayTextElement(val text: String) : InlineCompletionBlock {
  private var suffixInlay: Inlay<*>? = null
  private var blockInlay: Inlay<*>? = null

  override val offset: Int?
    get() = suffixInlay?.offset

  override val isEmpty: Boolean
    get() = suffixInlay == null && blockInlay == null

  override fun getBounds(): Rectangle? {
    val bounds = blockInlay?.bounds?.let { Rectangle(it) }
    suffixInlay?.bounds?.let { bounds?.add(Rectangle(it)) }
    return bounds
  }

  override fun render(editor: Editor, offset: Int) {
    if (text.isEmpty()) return
    val lines = text.lines()
    renderSuffix(editor, lines, offset)
    if (lines.size > 1) {
      renderBlock(lines.drop(1), editor, offset)
    }
  }

  override fun reset() {
    blockInlay?.let {
      Disposer.dispose(it)
      blockInlay = null
    }

    suffixInlay?.let {
      Disposer.dispose(it)
      suffixInlay = null
    }
  }

  override fun dispose() {
    blockInlay?.let { Disposer.dispose(it) }
    suffixInlay?.let { Disposer.dispose(it) }
    reset()
  }

  private fun renderSuffix(editor: Editor, lines: List<String>, offset: Int) {
    // the following is a hacky solution to the effect described in ML-977
    if (Registry.`is`("inline.completion.caret.forceLeanLeft")) {
      val visualPosition = editor.caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        editor.caretModel.moveToVisualPosition(leftLeaningPosition)
      }
    }

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
      // wrapping into a batch to notify inlay listeners after the hint is added
      val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, line)) ?: return@execute
      element.addActionAvailabilityHint(
        EditorActionAvailabilityHint("InsertInlineCompletionAction", EditorActionAvailabilityHint.AvailabilityCondition.CaretOnStart))
      Disposer.tryRegister(this, element)
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

    Disposer.tryRegister(this, element)
    blockInlay = element
  }
}
