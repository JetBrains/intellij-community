// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks

import com.intellij.ide.ui.UISettings
import com.intellij.util.ui.RegionPainter
import java.awt.Graphics2D
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.RegionPaintIcon
import java.awt.Component
import javax.swing.Icon

private val FOREGROUND = EditorColorsUtil.createColorKey("MnemonicIcon.foreground", JBColor(0x000000, 0xBBBBBB))
private val BACKGROUND = EditorColorsUtil.createColorKey("MnemonicIcon.background", JBColor(0xFEF7EC, 0x5B5341))
private val BORDER_COLOR = EditorColorsUtil.createColorKey("MnemonicIcon.borderColor", JBColor(0xF4AF3D, 0xD9A343))

internal class MnemonicPainter(ch: Char) : RegionPainter<Component?> {
  private val string = ch.toString()

  fun asIcon(size: Int): Icon = RegionPaintIcon(size, this).withIconPreScaled(false)

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    val foreground = EditorColorsUtil.getColor(c, FOREGROUND)
    val background = EditorColorsUtil.getColor(c, BACKGROUND)
    val borderColor = EditorColorsUtil.getColor(c, BORDER_COLOR)
    val thickness = if (borderColor == null || borderColor == background) 0 else 1
    val divisor = Registry.intValue("ide.mnemonic.icon.round", 0)
    val round = if (divisor > 0) width.coerceAtLeast(height) / divisor else null
    if (background != null) {
      g.paint = background
      RectanglePainter.FILL.paint(g, x, y, width, height, round)
    }
    if (foreground != null) {
      g.paint = foreground
      UISettings.setupAntialiasing(g)
      val frc = g.fontRenderContext
      val font = EditorFontType.PLAIN.globalFont

      val size1 = height - 2f * thickness
      val vector1 = font.deriveFont(size1).createGlyphVector(frc, string)
      val bounds1 = vector1.visualBounds

      val size2 = .8f * size1 * size1 / bounds1.height.toFloat()
      val vector2 = font.deriveFont(size2).createGlyphVector(frc, string)
      val bounds2 = vector2.visualBounds

      val dx = x - bounds2.x + .5 * (width - bounds2.width)
      val dy = y - bounds2.y + .5 * (height - bounds2.height)
      g.drawGlyphVector(vector2, dx.toFloat(), dy.toFloat())
    }
    if (thickness > 0) {
      g.paint = borderColor
      RectanglePainter.DRAW.paint(g, x, y, width, height, round)
    }
  }

  override fun toString() = "MnemonicIcon:$string"

  override fun hashCode() = string.hashCode()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val painter = other as? MnemonicPainter ?: return false
    return painter.string == string
  }
}
