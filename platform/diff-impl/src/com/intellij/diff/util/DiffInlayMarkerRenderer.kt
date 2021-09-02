// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util

import com.intellij.diff.tools.simple.SimpleAlignedDiffModel.Companion.getAlignedChangeColor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
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
      val inlayPosition = editor.visualPositionToXY(inlay.visualPosition)

      val y = if (isLastLine) {
        inlayPosition.y + editor.lineHeight
      }
      else {
        inlayPosition.y - inlayHeight
      }

      val preservedBackground = g.background
      g.color = getAlignedChangeColor(type, editor)
      g.fillRect(0, y, gutter.width, inlayHeight)
      g.color = preservedBackground
    }
  }

  override fun getPosition(): Position = Position.CUSTOM
}
