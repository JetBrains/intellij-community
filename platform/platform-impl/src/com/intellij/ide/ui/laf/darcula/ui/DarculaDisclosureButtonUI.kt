// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtilities
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class DarculaDisclosureButtonUI : BasicButtonUI() {

  companion object {

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): DarculaDisclosureButtonUI = DarculaDisclosureButtonUI()

    internal val ARC = JBValue.UIInteger("DisclosureButton.arc", 16)
    internal val TEXT_RIGHT_ICON_GAP = JBValue.UIInteger("DisclosureButton.textRightIconGap", 8)
    internal val DEFAULT_BACKGROUND = JBColor.namedColor("DisclosureButton.defaultBackground")
    internal val HOVER_BACKGROUND = JBColor.namedColor("DisclosureButton.hoverOverlay")
    internal val PRESSED_BACKGROUND = JBColor.namedColor("DisclosureButton.pressedOverlay")

    private var LEFT_MARGIN = 14
    private var RIGHT_MARGIN = 12
  }

  override fun installDefaults(b: AbstractButton?) {
    super.installDefaults(b)

    b?.border = DarculaDisclosureButtonBorder()
  }

  override fun paint(g: Graphics?, c: JComponent?) {
    if (c !is DisclosureButton || g == null) {
      super.paint(g, c)
      return
    }

    paintBackground(g, c)

    super.paint(g, c)

    c.arrowIcon?.let {
      val insets = c.getInsets()
      it.paintIcon(c, g, c.width - insets.right - JBUIScale.scale(RIGHT_MARGIN) - it.iconWidth,
                   insets.top + (c.height - insets.height - it.iconHeight) / 2)
    }
  }

  override fun paintIcon(g: Graphics?, c: JComponent?, iconRect: Rectangle?) {
    iconRect?.let {
      it.x += JBUIScale.scale(LEFT_MARGIN)
    }

    super.paintIcon(g, c, iconRect)
  }

  override fun paintText(g: Graphics?, c: JComponent?, textRect: Rectangle?, text: String?) {
    if (g == null || textRect == null) {
      return
    }

    if (c !is DisclosureButton) {
      super.paintText(g, c, textRect, text)
      return
    }

    textRect.x += JBUIScale.scale(LEFT_MARGIN)
    textRect.width = c.width - textRect.x - getExtraIconsSize(c).width - JBUIScale.scale(RIGHT_MARGIN)

    val fm = c.getFontMetrics(c.font)
    val clippedText = UIUtilities.clipStringIfNecessary(c as JComponent, fm, text, textRect.width)
    super.paintText(g, c as JComponent, textRect, clippedText)

    c.rightIcon?.let {
      val textWidth = fm.stringWidth(clippedText)
      val x = textRect.x + min(textRect.width, textWidth) + TEXT_RIGHT_ICON_GAP.get()
      val insets = c.getInsets()
      it.paintIcon(c, g, x, insets.top + (c.height - insets.height - it.iconHeight) / 2)
    }
  }

  override fun getPreferredSize(c: JComponent?): Dimension {
    val result = super.getPreferredSize(c)

    if (c is DisclosureButton) {
      val insets = c.getInsets()
      val minimumSize = getMinimumSize(c)
      val extraSize = getExtraIconsSize(c)
      result.width += extraSize.width
      result.height = max(result.height, extraSize.height)
      result.width = max(result.width + JBUIScale.scale(LEFT_MARGIN) + JBUIScale.scale(RIGHT_MARGIN), minimumSize.width) + insets.width
      result.height = max(result.height, minimumSize.height) + insets.height
    }

    return result
  }

  override fun getMinimumSize(c: JComponent?): Dimension {
    return JBDimension(72, 34)
  }

  private fun paintBackground(g: Graphics, c: DisclosureButton) {
    val r = Rectangle(0, 0, c.width, c.height)
    JBInsets.removeFrom(r, c.insets)
    DarculaNewUIUtil.fillRoundedRectangle(g, r, c.buttonBackground ?: DEFAULT_BACKGROUND, arc = ARC.float)

    val model = c.model
    val overlay =
      when {
        model.isArmed && model.isPressed -> PRESSED_BACKGROUND
        model.isRollover -> HOVER_BACKGROUND
        else -> return
      }
    DarculaNewUIUtil.fillRoundedRectangle(g, r, overlay, ARC.float)
  }

  private fun getExtraIconsSize(b: DisclosureButton): Dimension {
    val result = Dimension()
    b.rightIcon?.let {
      result.width += TEXT_RIGHT_ICON_GAP.get() + it.iconWidth
      result.height = max(result.height, it.iconHeight)
    }
    b.arrowIcon?.let {
      result.width += b.iconTextGap + it.iconWidth
      result.height = max(result.height, it.iconHeight)
    }
    return result
  }
}
