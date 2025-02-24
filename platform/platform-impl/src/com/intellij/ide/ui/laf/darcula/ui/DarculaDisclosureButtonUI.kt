// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.components.DisclosureButtonKind
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtilities
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
interface DarculaDisclosureButtonUIConfig {
  val ARC: JBValue.UIInteger
  val TEXT_RIGHT_ICON_GAP: JBValue.UIInteger
  val DEFAULT_BACKGROUND: JBColor?
  val HOVER_BACKGROUND: JBColor
  val PRESSED_BACKGROUND: JBColor
  val LEFT_MARGIN: Int
  val RIGHT_MARGIN: Int
  val HEIGHT: Int
}

private object DarculaDisclosureButtonUIDefaultConfig : DarculaDisclosureButtonUIConfig {
  override val ARC = JBValue.UIInteger("DisclosureButton.arc", 16)
  override val TEXT_RIGHT_ICON_GAP = JBValue.UIInteger("DisclosureButton.textRightIconGap", 8)
  override val DEFAULT_BACKGROUND = JBColor.namedColor("DisclosureButton.defaultBackground")
  override val HOVER_BACKGROUND = JBColor.namedColor("DisclosureButton.hoverOverlay")
  override val PRESSED_BACKGROUND = JBColor.namedColor("DisclosureButton.pressedOverlay")
  override val LEFT_MARGIN = 14
  override val RIGHT_MARGIN = 12
  override val HEIGHT = 34
}

private object DarculaDisclosureButtonUISimpleConfig : DarculaDisclosureButtonUIConfig {
  override val ARC = JBValue.UIInteger("DisclosureButton.simple.arc", 16)
  override val TEXT_RIGHT_ICON_GAP = JBValue.UIInteger("DisclosureButton.simple.textRightIconGap", 8)
  override val DEFAULT_BACKGROUND = null
  override val HOVER_BACKGROUND = JBColor.namedColor("DisclosureButton.hoverOverlay")
  override val PRESSED_BACKGROUND = JBColor.namedColor("DisclosureButton.pressedOverlay")
  override val LEFT_MARGIN = 9
  override val RIGHT_MARGIN = 9
  override val HEIGHT = 28
}

private val DRAW_DEBUG_LINES = false

@ApiStatus.Internal
class DarculaDisclosureButtonUI : BasicButtonUI() {
  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): DarculaDisclosureButtonUI = DarculaDisclosureButtonUI()

    fun getConfig(c: JComponent?): DarculaDisclosureButtonUIConfig = if ((c as? DisclosureButton)?.kind == DisclosureButtonKind.Klein) DarculaDisclosureButtonUISimpleConfig else DarculaDisclosureButtonUIDefaultConfig
  }

  override fun installDefaults(b: AbstractButton) {
    super.installDefaults(b)

    b.border = DarculaDisclosureButtonBorder()
  }

  override fun paint(g: Graphics?, c: JComponent?) {
    if (c !is DisclosureButton || g == null) {
      super.paint(g, c)
      return
    }

    paintBackground(g, c)

    super.paint(g, c)

    val arrowIconRect = Rectangle()
    c.arrowIcon?.let {
      val insets = c.getInsets()
      arrowIconRect.x = c.width - insets.right - JBUIScale.scale(getConfig(c).RIGHT_MARGIN) - it.iconWidth
      arrowIconRect.y = (c.height - it.iconHeight) / 2
      arrowIconRect.width = it.iconWidth
      arrowIconRect.height = it.iconHeight
      it.paintIcon(c, g, arrowIconRect.x, arrowIconRect.y)
    }

    if (DRAW_DEBUG_LINES) {
      g.color = Color.RED
      g.drawRect(arrowIconRect.x, arrowIconRect.y, arrowIconRect.width, arrowIconRect.height)
    }
  }

  override fun paintIcon(g: Graphics, c: JComponent, iconRect: Rectangle) {
    iconRect.x += JBUIScale.scale(getConfig(c).LEFT_MARGIN)

    if (DRAW_DEBUG_LINES) {
      g.color = Color.RED
      g.drawRect(iconRect.x, iconRect.y, iconRect.width, iconRect.height)
    }

    super.paintIcon(g, c, iconRect)
  }

  override fun paintText(g: Graphics, c: JComponent?, textRect: Rectangle, text: String?) {
    if (c !is DisclosureButton) {
      super.paintText(g, c, textRect, text)
      return
    }

    textRect.x += JBUIScale.scale(getConfig(c).LEFT_MARGIN)
    textRect.width = c.width - textRect.x - getExtraIconsSize(c).width - JBUIScale.scale(getConfig(c).RIGHT_MARGIN)

    val fm = c.getFontMetrics(c.font)
    val clippedText = UIUtilities.clipStringIfNecessary(c as JComponent, fm, text, textRect.width)
    super.paintText(g, c as JComponent, textRect, clippedText)

    if (DRAW_DEBUG_LINES) {
      g.color = Color.RED
      g.drawRect(textRect.x, textRect.y, textRect.width, textRect.height)
    }

    val rightIconRect = Rectangle()
    c.rightIcon?.let {
      val textWidth = fm.stringWidth(clippedText)
      val insets = c.getInsets()
      rightIconRect.x = textRect.x + min(textRect.width, textWidth) + getConfig(c).TEXT_RIGHT_ICON_GAP.get()
      rightIconRect.y = insets.top + (c.height - insets.height - it.iconHeight) / 2
      rightIconRect.width = it.iconWidth
      rightIconRect.height = it.iconHeight
      it.paintIcon(c, g, rightIconRect.x, rightIconRect.y)
    }
    if (DRAW_DEBUG_LINES) {
      g.color = Color.RED
      g.drawRect(rightIconRect.x, rightIconRect.y, rightIconRect.width, rightIconRect.height)
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
      result.width = max(result.width + JBUIScale.scale(getConfig(c).LEFT_MARGIN) + JBUIScale.scale(getConfig(c).RIGHT_MARGIN), minimumSize.width) + insets.width
      result.height = max(result.height, minimumSize.height) + insets.height
    }

    return result
  }

  override fun getMinimumSize(c: JComponent?): Dimension {
    return JBDimension(72, getConfig(c).HEIGHT)
  }

  private fun paintBackground(g: Graphics, c: DisclosureButton) {
    val r = Rectangle(0, 0, c.width, c.height)
    JBInsets.removeFrom(r, c.insets)

    val defaultBg = c.buttonBackground ?: getConfig(c).DEFAULT_BACKGROUND
    if (defaultBg != null) {
      DarculaNewUIUtil.fillRoundedRectangle(g, r, defaultBg, arc = getConfig(c).ARC.float)
    }

    val model = c.model
    val overlay = when {
      model.isArmed && model.isPressed -> getConfig(c).PRESSED_BACKGROUND
      model.isRollover -> getConfig(c).HOVER_BACKGROUND
      else -> return
    }
    DarculaNewUIUtil.fillRoundedRectangle(g, r, overlay, getConfig(c).ARC.float)
  }

  private fun getExtraIconsSize(b: DisclosureButton): Dimension {
    val result = Dimension()
    b.rightIcon?.let {
      result.width += getConfig(b).TEXT_RIGHT_ICON_GAP.get() + it.iconWidth
      result.height = max(result.height, it.iconHeight)
    }
    b.arrowIcon?.let {
      result.width += b.iconTextGap + it.iconWidth
      // Chevron is in square even though it is rectangular. Subtract some width to compensate for that
      if (it === AllIcons.General.ChevronRight) {
        result.width -= 8
      }
      result.height = max(result.height, it.iconHeight)
    }
    return result
  }
}
