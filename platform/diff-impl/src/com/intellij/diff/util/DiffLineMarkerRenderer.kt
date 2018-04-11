/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.util

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
  private val myIgnoredFoldingOutline: Boolean,
  private val myResolved: Boolean,
  private val myExcluded: Boolean,
  private val myHideWithoutLineNumbers: Boolean,
  private val myEmptyRange: Boolean,
  private val myFirstLine: Boolean,
  private val myLastLine: Boolean
) : LineMarkerRendererEx {

  override fun paint(editor: Editor, g: Graphics, range: Rectangle) {
    editor as EditorEx
    g as Graphics2D
    val gutter = editor.gutterComponentEx

    var x1 = 0
    val x2 = x1 + gutter.width

    var y1: Int
    var y2: Int
    if (myEmptyRange && myLastLine) {
      y1 = DiffDrawUtil.lineToY(editor, DiffUtil.getLineCount(editor.document))
      y2 = y1
    }
    else {
      val startLine = editor.document.getLineNumber(myHighlighter.startOffset)
      val endLine = editor.document.getLineNumber(myHighlighter.endOffset) + 1
      y1 = DiffDrawUtil.lineToY(editor, startLine)
      y2 = if (myEmptyRange) y1 else DiffDrawUtil.lineToY(editor, endLine)
    }

    if (myEmptyRange && myFirstLine) {
      y1++
      y2++
    }

    if (myHideWithoutLineNumbers && !editor.getSettings().isLineNumbersShown) {
      // draw only in "editor" part of the gutter (rightmost part of foldings' "[+]" )
      x1 = gutter.whitespaceSeparatorOffset
    }
    else {
      val annotationsOffset = gutter.annotationsAreaOffset
      val annotationsWidth = gutter.annotationsAreaWidth
      if (annotationsWidth != 0) {
        drawMarker(editor, g, x1, annotationsOffset, y1, y2)
        x1 = annotationsOffset + annotationsWidth
      }
    }

    if (myExcluded) {
      val xOutline = gutter.whitespaceSeparatorOffset
      drawMarker(editor, g, xOutline, x2, y1, y2, paintBackground = false, paintBorder = true) // over "editor"
      drawMarker(editor, g, x1, xOutline, y1, y2, paintBackground = true, paintBorder = true, useIgnoredBackgroundColor = true) // over "gutter"
    }
    else if (myIgnoredFoldingOutline) {
      val xOutline = gutter.whitespaceSeparatorOffset
      drawMarker(editor, g, xOutline, x2, y1, y2, useIgnoredBackgroundColor = true) // over "editor"
      drawMarker(editor, g, x1, xOutline, y1, y2) // over "gutter"
    }
    else {
      drawMarker(editor, g, x1, x2, y1, y2)
    }
  }

  private fun drawMarker(editor: Editor, g: Graphics2D,
                         x1: Int, x2: Int, y1: Int, y2: Int,
                         paintBackground: Boolean = !myResolved,
                         paintBorder: Boolean = myResolved,
                         dottedLine: Boolean = myResolved,
                         useIgnoredBackgroundColor: Boolean = false) {
    if (x1 >= x2) return

    val color = myDiffType.getColor(editor)
    if (y2 - y1 > 2) {
      if (paintBackground) {
        g.color = if (useIgnoredBackgroundColor) myDiffType.getIgnoredColor(editor) else color
        g.fillRect(x1, y1, x2 - x1, y2 - y1)
      }
      if (paintBorder) {
        DiffDrawUtil.drawChunkBorderLine(g, x1, x2, y1, color, false, dottedLine)
        DiffDrawUtil.drawChunkBorderLine(g, x1, x2, y2 - 1, color, false, dottedLine)
      }
    }
    else {
      // range is empty - insertion or deletion
      // Draw 2 pixel line in that case
      DiffDrawUtil.drawChunkBorderLine(g, x1, x2, y1 - 1, color, true, dottedLine)
    }
  }

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM
}
