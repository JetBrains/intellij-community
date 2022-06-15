// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.IconWrapperWithToolTip
import com.intellij.ui.JBColor
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.RegionPainter
import java.awt.Component
import java.awt.Graphics2D
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

  val icon by lazy { createIcon(IconSize.REGULAR, 1) }
  val gutterIcon by lazy { createIcon(IconSize.GUTTER, 0) }

  private fun createIcon(size: IconSize, insets: Int): Icon = BookmarkIcon(mnemonic, size, insets)

  companion object {
    @JvmStatic
    fun get(mnemonic: Char) = values().firstOrNull { it.mnemonic == mnemonic } ?: DEFAULT
  }
}

internal enum class IconSize {
  GUTTER,
  REGULAR,
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
  size: IconSize,
  insets: Int,
) : IconWrapperWithToolTip(createBookmarkIcon(mnemonic, size, insets),
                           LangBundle.messagePointer("tooltip.bookmarked")) {
  companion object {
    private fun createBookmarkIcon(mnemonic: Char, size: IconSize, insets: Int): Icon {
      if (mnemonic == 0.toChar()) {
        return when (size) {
          IconSize.GUTTER -> AllIcons.Gutter.Bookmark
          else -> AllIcons.Nodes.Bookmark
        }
      }
      val painter = MnemonicPainter(mnemonic.toString())
      val paintSize = when (size) {
        IconSize.GUTTER -> 12
        else -> 16
      }
      return RegionPaintIcon(paintSize, paintSize, insets, painter).withIconPreScaled(false)
    }
  }
}
