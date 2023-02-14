// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.IconReplacer
import com.intellij.ui.IconWrapperWithToolTip
import com.intellij.ui.JBColor
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

  val icon by lazy { createIcon(IconSize.REGULAR) }
  val gutterIcon by lazy { createIcon(IconSize.GUTTER) }

  private fun createIcon(size: IconSize): Icon = BookmarkIcon(mnemonic, size)

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

private class MnemonicPainter(val icon: Icon, val mnemonic: String) : RegionPainter<Component?> {
  override fun toString() = "BookmarkMnemonicIcon:$mnemonic"
  override fun hashCode() = mnemonic.hashCode()
  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    val painter = other as? MnemonicPainter ?: return false
    return painter.mnemonic == mnemonic
  }

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    icon.paintIcon(null, g, x, y)

    val foreground = EditorColorsUtil.getColor(null, MNEMONIC_ICON_FOREGROUND)
    g.paint = foreground
    UISettings.setupAntialiasing(g)
    val frc = g.fontRenderContext
    val font = EditorFontType.PLAIN.globalFont

    val size1 = .75f * height
    val vector1 = font.deriveFont(size1).createGlyphVector(frc, mnemonic)
    val bounds1 = vector1.visualBounds

    val dx = x - bounds1.x + .5 * (width - bounds1.width)
    val dy = y - bounds1.y + .5 * (height - bounds1.height)
    g.drawGlyphVector(vector1, dx.toFloat(), dy.toFloat())
  }
}

class BookmarkIcon : IconWrapperWithToolTip {
  val mnemonic: Char

  internal constructor(mnemonic: Char, size: IconSize) : this(mnemonic, createBookmarkIcon(mnemonic, size))

  private constructor(mnemonic: Char, icon: Icon)  : super(icon, LangBundle.messagePointer("tooltip.bookmarked")) {
    this.mnemonic = mnemonic
  }

  override fun replaceBy(replacer: IconReplacer): BookmarkIcon {
    return BookmarkIcon(mnemonic, replacer.replaceIcon(retrieveIcon()))
  }

  companion object {
    @JvmStatic
    private fun createBookmarkIcon(mnemonic: Char, size: IconSize): Icon {
      if (mnemonic == 0.toChar()) {
        return when (size) {
          IconSize.GUTTER -> AllIcons.Gutter.Bookmark
          else -> AllIcons.Nodes.Bookmark
        }
      }
      val icon = when (size) {
        IconSize.GUTTER -> AllIcons.Gutter.Mnemonic
        else -> AllIcons.Nodes.Mnemonic
      }
      val painter = MnemonicPainter(icon, mnemonic.toString())
      val paintSize = when (size) {
        IconSize.GUTTER -> 12
        else -> 16
      }
      return RegionPaintIcon(paintSize, paintSize, 0, painter).withIconPreScaled(false)
    }
  }
}
