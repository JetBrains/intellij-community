// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util

import com.intellij.diff.util.DiffDrawUtil.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

internal class DiffLineMarkerRenderer(
  private val myHighlighter: RangeHighlighter,
  private val myDiffType: TextDiffType,
  private val myEditorMode: PaintMode,
  private val myGutterMode: PaintMode,
  private val myHideWithoutLineNumbers: Boolean,
  private val myEmptyRange: Boolean,
  private val myFirstLine: Boolean,
  private val myLastLine: Boolean,
  private val alignedSides: Boolean
) : LineMarkerRendererEx {

  override fun paint(editor: Editor, g: Graphics, range: Rectangle) {
    editor as EditorEx
    g as Graphics2D
    val gutter = editor.gutterComponentEx

    var x1 = 0
    val x2 = gutter.width

    val startLine: Int
    val endLine: Int
    if (myEmptyRange) {
      if (myLastLine) {
        startLine = DiffUtil.getLineCount(editor.document)
      }
      else {
        startLine = editor.document.getLineNumber(myHighlighter.startOffset)
      }
      endLine = startLine
    }
    else {
      startLine = editor.document.getLineNumber(myHighlighter.startOffset)
      endLine = editor.document.getLineNumber(myHighlighter.endOffset) + 1
    }
    val (y1, y2) = getGutterMarkerPaintRange(editor, startLine, endLine)

    if (myHideWithoutLineNumbers && !editor.getSettings().isLineNumbersShown) {
      // draw only in "editor" part of the gutter (rightmost part of foldings' "[+]" )
      x1 = gutter.whitespaceSeparatorOffset
    }
    else {
      val annotationsOffset = gutter.annotationsAreaOffset
      val annotationsWidth = gutter.annotationsAreaWidth
      if (annotationsWidth != 0) {
        drawMarker(editor, g, x1, annotationsOffset, y1, y2, alignedSides, myGutterMode)
        x1 = annotationsOffset + annotationsWidth
      }
    }

    if (myEditorMode == myGutterMode) {
      drawMarker(editor, g, x1, x2, y1, y2, alignedSides, myGutterMode)
    }
    else {
      val xOutline = gutter.whitespaceSeparatorOffset
      drawMarker(editor, g, xOutline, x2, y1, y2, alignedSides, myEditorMode)
      drawMarker(editor, g, x1, xOutline, y1, y2, alignedSides, myGutterMode)
    }
  }

  private fun drawMarker(editor: Editor, g: Graphics2D,
                         x1: Int, x2: Int, y1: Int, y2: Int,
                         alignedSides: Boolean, mode: PaintMode) {
    if (x1 >= x2) return

    val dottedLine = mode.border == BorderType.DOTTED
    val color = myDiffType.getColor(editor)
    val backgroundColor = when (mode.background) {
      BackgroundType.NONE -> null
      BackgroundType.DEFAULT -> color
      BackgroundType.IGNORED -> myDiffType.getIgnoredColor(editor)
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
