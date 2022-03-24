// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.ide.ui.UISettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconWrapperWithToolTip
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBScalableIcon
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.RegionPainter
import java.awt.Component
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.Icon

enum class BookmarkType(val mnemonic: Char) {
  DIGIT_1('1'), DIGIT_2('2'), DIGIT_3('3'), DIGIT_4('4'), DIGIT_5('5'),
  DIGIT_6('6'), DIGIT_7('7'), DIGIT_8('8'), DIGIT_9('9'), DIGIT_0('0'),

  LETTER_A('A'), LETTER_B('B'), LETTER_C('C'), LETTER_D('D'),
  LETTER_E('E'), LETTER_F('F'), LETTER_G('G'), LETTER_H('H'),
  LETTER_I('I'), LETTER_J('J'), LETTER_K('K'), LETTER_L('L'),
  LETTER_M('M'), LETTER_N('N'), LETTER_O('O'), LETTER_P('P'),
  LETTER_Q('Q'), LETTER_R('R'), LETTER_S('S'), LETTER_T('T'),
  LETTER_U('U'), LETTER_V('V'), LETTER_W('W'),
  LETTER_X('X'), LETTER_Y('Y'), LETTER_Z('Z'),

  DEFAULT(0.toChar());

  val icon by lazy { createIcon(16, 1) }
  val gutterIcon by lazy { createIcon(12, 0) }

  private fun createIcon(size: Int, insets: Int): Icon = BookmarkIcon(mnemonic, size, insets)

  companion object {
    @JvmStatic
    fun get(mnemonic: Char) = values().firstOrNull { it.mnemonic == mnemonic } ?: DEFAULT
  }
}


private val BOOKMARK_ICON_BACKGROUND = EditorColorsUtil.createColorKey("Bookmark.iconBackground", JBColor(0xF7C777, 0xAA8542))

private class BookmarkPainter : RegionPainter<Component?> {
  override fun toString() = "BookmarkIcon"
  override fun hashCode() = toString().hashCode()
  override fun equals(other: Any?) = other === this || other is BookmarkPainter

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    val background = EditorColorsUtil.getColor(c, BOOKMARK_ICON_BACKGROUND)
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
}


private val MNEMONIC_ICON_FOREGROUND = EditorColorsUtil.createColorKey("Bookmark.Mnemonic.iconForeground", JBColor(0x000000, 0xBBBBBB))
private val MNEMONIC_ICON_BACKGROUND = EditorColorsUtil.createColorKey("Bookmark.Mnemonic.iconBackground", JBColor(0xFEF7EC, 0x5B5341))
private val MNEMONIC_ICON_BORDER_COLOR = EditorColorsUtil.createColorKey("Bookmark.Mnemonic.iconBorderColor", JBColor(0xF4AF3D, 0xD9A343))

private class MnemonicPainter(val mnemonic: String) : RegionPainter<Component?> {
  override fun toString() = "BookmarkMnemonicIcon:$mnemonic"
  override fun hashCode() = mnemonic.hashCode()
  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val painter = other as? MnemonicPainter ?: return false
    return painter.mnemonic == mnemonic
  }

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    val foreground = EditorColorsUtil.getColor(c, MNEMONIC_ICON_FOREGROUND)
    val background = EditorColorsUtil.getColor(c, MNEMONIC_ICON_BACKGROUND)
    val borderColor = EditorColorsUtil.getColor(c, MNEMONIC_ICON_BORDER_COLOR)
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

      val size1 = .8f * height
      val vector1 = font.deriveFont(size1).createGlyphVector(frc, mnemonic)
      val bounds1 = vector1.visualBounds

      val size2 = .8f * size1 * size1 / bounds1.height.toFloat()
      val vector2 = font.deriveFont(size2).createGlyphVector(frc, mnemonic)
      val bounds2 = vector2.visualBounds

      val dx = x - bounds2.x + .5 * (width - bounds2.width)
      val dy = y - bounds2.y + .5 * (height - bounds2.height)
      g.drawGlyphVector(vector2, dx.toFloat(), dy.toFloat())
    }
    if (borderColor != null && borderColor != background) {
      g.paint = borderColor
      RectanglePainter.DRAW.paint(g, x, y, width, height, round)
    }
  }
}

class BookmarkIcon internal constructor(
  val mnemonic: Char,
  size: Int,
  insets: Int,
) : IconWrapperWithToolTip(createBookmarkIcon(mnemonic, size, insets),
                           LangBundle.messagePointer("tooltip.bookmarked")) {
  companion object {
    private fun createBookmarkIcon(mnemonic: Char, size: Int, insets: Int): JBScalableIcon {
      val painter = if (mnemonic == 0.toChar()) BookmarkPainter() else MnemonicPainter(mnemonic.toString())
      return RegionPaintIcon(size, size, insets, painter).withIconPreScaled(false)
    }
  }
}
