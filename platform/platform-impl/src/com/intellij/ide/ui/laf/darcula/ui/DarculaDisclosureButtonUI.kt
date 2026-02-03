// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaDisclosureButtonUI.Companion.intersectsWithAdditionalActionsButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtilities
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicButtonListener
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.max
import kotlin.math.min

private const val DRAW_DEBUG_LINES = false

@ApiStatus.Internal
class DarculaDisclosureButtonUI(val uiComponent: JComponent) : BasicButtonUI() {
  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): DarculaDisclosureButtonUI = DarculaDisclosureButtonUI(c)

    internal fun getAdditionalButtonArc(): Int = JBUI.scale(6)
    internal fun getAdditionalButtonBorder(): Int = JBUI.scale(2)
    internal fun additionalActionsIcon(isHover: Boolean): Icon {
      return if (isHover) AllIcons.Ide.Notification.GearHover else AllIcons.Ide.Notification.Gear
    }

    internal fun intersectsWithAdditionalActionsButton(c: DisclosureButton, e: MouseEvent): Boolean {
      val actionBounds = getAdditionalActionsButtonBounds(c) ?: return false
      return actionBounds.contains(e.point)
    }

    @ApiStatus.Internal
    internal fun getAdditionalActionsButtonBounds(c: DisclosureButton): Rectangle? {
      if (c.additionalAction == null) return null
      val gearIcon = additionalActionsIcon(false)

      val borderSize = getAdditionalButtonBorder()
      val actionBounds = Rectangle()
      actionBounds.width = gearIcon.iconWidth + borderSize * 2
      actionBounds.height = gearIcon.iconHeight + borderSize * 2

      actionBounds.x = c.width - c.insets.right - c.rightMargin - actionBounds.width
      actionBounds.y = (c.height - actionBounds.height) / 2

      return actionBounds
    }
  }

  private val mouseHoverListener = MouseHoverListener()
  private val componentHoverListener = ComponentHoverListener()

  @ApiStatus.Internal
  private var isAdditionalActionsButtonHovered: Boolean = false
    private set(value) {
      if (field != value) {
        field = value
        uiComponent.repaint()
      }
    }

  override fun installDefaults(b: AbstractButton) {
    super.installDefaults(b)

    b.border = DarculaDisclosureButtonBorder()
  }

  override fun installListeners(b: AbstractButton?) {
    super.installListeners(b)

    uiComponent.addComponentListener(componentHoverListener)
    uiComponent.addMouseListener(mouseHoverListener)
    uiComponent.addMouseMotionListener(mouseHoverListener)
  }

  override fun uninstallListeners(b: AbstractButton?) {
    super.uninstallListeners(b)

    uiComponent.removeComponentListener(componentHoverListener)
    uiComponent.removeMouseListener(mouseHoverListener)
    uiComponent.removeMouseMotionListener(mouseHoverListener)
  }

  override fun createButtonListener(b: AbstractButton): BasicButtonListener {
    return DarculaDisclosureButtonListener(b)
  }

  override fun paint(g: Graphics?, c: JComponent?) {
    if (c !is DisclosureButton || g == null) {
      super.paint(g, c)
      return
    }

    paintBackground(g, c)

    super.paint(g, c)

    val insets = c.getInsets()

    val actionBounds = getAdditionalActionsButtonBounds(c)
    if (actionBounds != null) {
      if (isAdditionalActionsButtonHovered) {
        g as Graphics2D

        val arc = getAdditionalButtonArc()
        val color = c.pressedBackground
        if (color != null) {
          g.color = color
          g.fillRoundRect(actionBounds.x, actionBounds.y, actionBounds.width, actionBounds.height, arc, arc)
        }
      }

      val icon = additionalActionsIcon(isAdditionalActionsButtonHovered)
      val iconX = actionBounds.x + (actionBounds.width - icon.iconWidth) / 2
      val iconY = actionBounds.y + (actionBounds.height - icon.iconHeight) / 2
      icon.paintIcon(c, g, iconX, iconY)
    }
    else {
      val arrowIconRect = Rectangle()
      c.arrowIcon?.let {
        arrowIconRect.x = c.width - insets.right - c.rightMargin - it.iconWidth
        arrowIconRect.y = (c.height - it.iconHeight) / 2
        arrowIconRect.width = it.iconWidth
        arrowIconRect.height = it.iconHeight
        it.paintIcon(c, g, arrowIconRect.x, arrowIconRect.y)
      }

      if (DRAW_DEBUG_LINES) {
        g.color = JBColor.RED
        g.drawRect(arrowIconRect.x, arrowIconRect.y, arrowIconRect.width, arrowIconRect.height)
      }
    }
  }

  override fun paintIcon(g: Graphics, c: JComponent, iconRect: Rectangle) {
    if (c !is DisclosureButton) return super.paintIcon(g, c, iconRect)

    iconRect.x += c.leftMargin

    if (DRAW_DEBUG_LINES) {
      g.color = JBColor.RED
      g.drawRect(iconRect.x, iconRect.y, iconRect.width, iconRect.height)
    }

    super.paintIcon(g, c, iconRect)
  }

  override fun paintText(g: Graphics, c: JComponent?, textRect: Rectangle, text: String?) {
    if (c !is DisclosureButton) {
      super.paintText(g, c, textRect, text)
      return
    }

    textRect.x += c.leftMargin
    textRect.width = c.width - textRect.x - getExtraIconsSize(c).width - c.rightMargin

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
      rightIconRect.x = textRect.x + min(textRect.width, textWidth) + c.textRightIconGap
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
      result.width = max(result.width + c.leftMargin + c.rightMargin, minimumSize.width) + insets.width
      result.height = max(result.height, minimumSize.height) + insets.height
    }

    return result
  }

  override fun getMinimumSize(c: JComponent?): Dimension {
    if (c !is DisclosureButton) return super.getMinimumSize(c)
    return JBDimension(72, c.buttonHeight)
  }

  private fun paintBackground(g: Graphics, c: DisclosureButton) {
    val r = Rectangle(0, 0, c.width, c.height)
    JBInsets.removeFrom(r, c.insets)

    val defaultBg = c.buttonBackground ?: c.defaultBackground
    if (defaultBg != null) {
      DarculaNewUIUtil.fillRoundedRectangle(g, r, defaultBg, arc = c.arc.toFloat())
    }

    val model = c.model
    val overlay = when {
      model.isArmed && model.isPressed -> c.pressedBackground
      model.isRollover -> c.hoverBackground
      else -> return
    }
    if (overlay != null) {
      DarculaNewUIUtil.fillRoundedRectangle(g, r, overlay, c.arc.toFloat())
    }
  }

  private fun getExtraIconsSize(b: DisclosureButton): Dimension {
    val result = Dimension()
    b.rightIcon?.let {
      result.width += b.textRightIconGap + it.iconWidth
      result.height = max(result.height, it.iconHeight)
    }
    val actionBounds = getAdditionalActionsButtonBounds(b)
    if (actionBounds != null) {
      result.width += b.iconTextGap + actionBounds.width
      result.height = max(result.height, actionBounds.height)
    }
    else {
      b.arrowIcon?.let {
        result.width += b.iconTextGap + it.iconWidth
        result.height = max(result.height, it.iconHeight)
      }
    }
    return result
  }

  private inner class MouseHoverListener : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent) {
      val button = uiComponent as? DisclosureButton ?: return
      isAdditionalActionsButtonHovered = intersectsWithAdditionalActionsButton(button, e)
    }

    override fun mouseExited(e: MouseEvent) {
      isAdditionalActionsButtonHovered = false
    }

    override fun mouseMoved(e: MouseEvent) {
      val button = uiComponent as? DisclosureButton ?: return
      isAdditionalActionsButtonHovered = intersectsWithAdditionalActionsButton(button, e)
    }
  }

  private inner class ComponentHoverListener : ComponentAdapter() {
    override fun componentHidden(e: ComponentEvent?) {
      isAdditionalActionsButtonHovered = false
    }
  }
}

private class DarculaDisclosureButtonListener(private val b: AbstractButton) : BasicButtonListener(b) {
  override fun mousePressed(e: MouseEvent) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      val c = b as? DisclosureButton
      if (c != null && intersectsWithAdditionalActionsButton(c, e)) {
        return // suppress default click handler
      }
    }

    super.mousePressed(e)
  }

  override fun mouseReleased(e: MouseEvent) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      val c = b as? DisclosureButton
      if (c != null && intersectsWithAdditionalActionsButton(c, e)) {
        c.invokeAdditionalAction(e)
        e.consume()
        return
      }
    }

    super.mouseReleased(e)
  }
}
