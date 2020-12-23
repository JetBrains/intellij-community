// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks

import com.intellij.util.ui.RegionPainter
import java.awt.Graphics2D
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.RegionPaintIcon
import java.awt.Component
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.Icon

private val BACKGROUND = EditorColorsUtil.createColorKey("BookmarkIcon.background", JBColor(0xF7C777, 0xAA8542))

internal class BookmarkPainter : RegionPainter<Component?> {

  fun asIcon(size: Int): Icon = RegionPaintIcon(size, this).withIconPreScaled(false)

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    val background = EditorColorsUtil.getColor(c, BACKGROUND)
    if (background != null) {
      val xL = width / 6f
      val xR = width - xL
      val xC = width / 2f

      val yT = height / 24f
      val yB = height - yT
      val yC = yB + xL - xC

      val path = Path2D.Float()
      path.moveTo(x + xL, y + yT)
      path.lineTo(x + xL, y + yB)
      path.lineTo(x + xC, y + yC)
      path.lineTo(x + xR, y + yB)
      path.lineTo(x + xR, y + yT)
      path.closePath()

      g.paint = background
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.fill(path)
    }
  }

  override fun toString() = "BookmarkIcon"

  override fun hashCode() = toString().hashCode()

  override fun equals(other: Any?) = other === this || other is BookmarkPainter
}
