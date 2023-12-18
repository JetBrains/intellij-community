// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.util.DiffDrawUtil.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

@ApiStatus.Internal
open class DiffLineMarkerRenderer(
  val highlighter: RangeHighlighter,
  val diffType: TextDiffType,
  val editorMode: PaintMode,
  val gutterMode: PaintMode,
  val hideWithoutLineNumbers: Boolean,
  val isEmptyRange: Boolean,
  val isFirstLine: Boolean,
  val isLastLine: Boolean,
  val alignedSides: Boolean
) : LineMarkerRendererEx {

  override fun paint(editor: Editor, g: Graphics, range: Rectangle) {
    editor as EditorEx
    g as Graphics2D
    val gutter = editor.gutterComponentEx

    var x1 = 0
    val x2 = gutter.width

    val startLine: Int
    val endLine: Int
    if (isEmptyRange) {
      if (isLastLine) {
        startLine = DiffUtil.getLineCount(editor.document)
      }
      else {
        startLine = editor.document.getLineNumber(highlighter.startOffset)
      }
      endLine = startLine
    }
    else {
      startLine = editor.document.getLineNumber(highlighter.startOffset)
      endLine = editor.document.getLineNumber(highlighter.endOffset) + 1
    }
    val (y1, y2) = getGutterMarkerPaintRange(editor, startLine, endLine)

    if (hideWithoutLineNumbers && !editor.getSettings().isLineNumbersShown) {
      // draw only in "editor" part of the gutter (rightmost part of foldings' "[+]" )
      x1 = gutter.whitespaceSeparatorOffset
    }
    else {
      val annotationsOffset = gutter.annotationsAreaOffset
      val annotationsWidth = gutter.annotationsAreaWidth
      if (annotationsWidth != 0) {
        drawMarker(editor, g, x1, annotationsOffset, y1, y2, alignedSides, gutterMode)
        x1 = annotationsOffset + annotationsWidth
      }
    }

    if (editorMode == gutterMode) {
      drawMarker(editor, g, x1, x2, y1, y2, alignedSides, gutterMode)
    }
    else {
      val xOutline = gutter.whitespaceSeparatorOffset
      drawMarker(editor, g, xOutline, x2, y1, y2, alignedSides, editorMode)
      drawMarker(editor, g, x1, xOutline, y1, y2, alignedSides, gutterMode)
    }
  }

  fun drawMarker(editor: Editor, g: Graphics2D,
                 x1: Int, x2: Int, y1: Int, y2: Int,
                 alignedSides: Boolean, mode: PaintMode) {
    if (x1 >= x2) return

    val dottedLine = mode.border == BorderType.DOTTED
    val color = diffType.getColor(editor)
    val backgroundColor = when (mode.background) {
      BackgroundType.NONE -> null
      BackgroundType.DEFAULT -> color
      BackgroundType.IGNORED -> diffType.getIgnoredColor(editor)
    }

    val isEmptyRange = y2 - y1 <= 2
    if (!isEmptyRange) {
      if (backgroundColor != null) {
        g.color = backgroundColor
        g.fillRect(x1, y1, x2 - x1, y2 - y1)
      }
      if (mode.border != BorderType.NONE && !alignedSides) {
        drawChunkBorderLine(g, x1, x2, y1, color, false, dottedLine)
        drawChunkBorderLine(g, x1, x2, y2 - 1, color, false, dottedLine)
      }
    }
    else if (!alignedSides) {
      // range is empty - insertion or deletion
      // Draw 2 pixel line in that case
      drawChunkBorderLine(g, x1, x2, y1 - 1, color, true, dottedLine)
    }
  }

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM
}
