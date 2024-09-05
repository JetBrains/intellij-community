// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.notebooks.ui.isFoldingEnabledKey
import java.awt.Graphics
import java.awt.Rectangle

abstract class NotebookLineMarkerRenderer(private val inlayId: Long? = null) : LineMarkerRendererEx {
  fun getInlayId(): Long? = inlayId

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

  protected fun getInlayBounds(editor: EditorEx, linesRange: IntRange): Rectangle? {
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)

    val inlay = inlays.firstOrNull { it is RangeMarkerEx && it.id == inlayId }
    return inlay?.bounds
  }
}

class NotebookAboveCodeCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return

    val bottomRectHeight = editor.notebookAppearance.cellBorderHeight / 2
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y + inlayBounds.height - bottomRectHeight, bottomRectHeight)
  }
}

class NotebookBelowCellCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter,
                                                    inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y, inlayBounds.height)
  }
}

class MarkdownCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    if (editor.getUserData(isFoldingEnabledKey) != true) {
      editor as EditorImpl
      val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
      val inlayBounds = getInlayBounds(editor, lines) ?: return
      paintCellGutter(inlayBounds, lines, editor, g, r)
    }
  }
}

class NotebookCodeCellBackgroundLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintNotebookCellBackgroundGutter(editor, g, r, lines, top, height) {
      paintCaretRow(editor, g, lines)
    }
  }
}

class NotebookTextCellBackgroundLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl

    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintCaretRow(editor, g, lines)
    if (editor.getUserData(isFoldingEnabledKey) != true) {
      val appearance = editor.notebookAppearance
      appearance.getCellStripeColor(editor, lines)?.let {
        paintCellStripe(appearance, g, r, it, top, height, editor)
      }
    }
  }
}

class NotebookCellToolbarGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(
  inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y, inlayBounds.height)
  }
}