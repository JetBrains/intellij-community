// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util

import com.intellij.diff.tools.simple.AlignableChange.Companion.getAlignedChangeColor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.LineMarkerRendererEx.Position
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class DiffInlayMarkerRenderer(
  private val type: TextDiffType,
  private val inlay: Inlay<*>,
  private val isLastLine: Boolean,
) : LineMarkerRendererEx {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorEx
    g as Graphics2D
    if (inlay is RangeMarker) {
      val gutter = editor.gutterComponentEx

      val inlayHeight = inlay.heightInPixels

      val preservedBackground = g.background
      g.color = getAlignedChangeColor(type, editor)
      g.fillRect(0, getVisualLineAreaStartY(inlay), gutter.width, inlayHeight)
      g.color = preservedBackground
    }
  }

  private fun getVisualLineAreaStartY(inlay: Inlay<*>): Int {
    val editor = inlay.editor
    val visualLineY = editor.visualLineToY(inlay.visualPosition.line)
    val inlayHeight = inlay.heightInPixels
    val (beforeHeight, afterHeight) = getBeforeAndAfterInlaysHeight(inlay)

    return when {
      isLastLine -> visualLineY - (afterHeight - beforeHeight) + editor.lineHeight
      afterHeight == beforeHeight -> visualLineY - afterHeight - inlayHeight
      else -> visualLineY - (afterHeight + inlayHeight)
    }
  }

  private fun getBeforeAndAfterInlaysHeight(inlayThreshold: Inlay<*>): Pair<Int, Int> {
    val before = mutableListOf<Inlay<*>>()
    val after = mutableListOf<Inlay<*>>()
    var isBefore = true

    for (inlay in inlayThreshold.editor.inlayModel.getBlockElementsForVisualLine(inlayThreshold.visualPosition.line, !isLastLine)) {
      if (inlay == inlayThreshold) {
        isBefore = false
        continue
      }
      if (isBefore) {
        before += inlay
      }
      else {
        after += inlay
      }
    }

    return EditorUtil.getTotalInlaysHeight(before) to EditorUtil.getTotalInlaysHeight(after)
  }

  override fun getPosition(): Position = Position.CUSTOM
}
