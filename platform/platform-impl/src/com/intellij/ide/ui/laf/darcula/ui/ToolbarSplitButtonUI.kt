// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.AbstractToolbarCombo
import com.intellij.openapi.wm.impl.ToolbarSplitButton
import com.intellij.ui.ClickListener
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.max

private const val SEPARATOR_WIDTH = 1

private const val DEAD_ZONE = 0
private const val ACTION_ZONE = 1
private const val EXPAND_ZONE = 2

@ApiStatus.Internal
class ToolbarSplitButtonUI : AbstractToolbarComboUI(), PropertyChangeListener {

  private val clickListener = MyClickListener()
  private val hoverListener = MyHoverListener()

  companion object {
    @JvmStatic
    fun createUI(c: JComponent): ToolbarSplitButtonUI = ToolbarSplitButtonUI()
  }

  override fun installUI(c: JComponent) {
    val widget = c as ToolbarSplitButton
    setUIDefaults(widget)
    widget.addPropertyChangeListener(this)
    tryUpdateHtmlRenderer(widget, widget.text)
    hoverListener.addTo(widget)
    clickListener.installOn(widget)
  }

  override fun uninstallUI(c: JComponent) {
    val widget = c as ToolbarSplitButton
    widget.removePropertyChangeListener(this)
    tryUpdateHtmlRenderer(widget, "")
    hoverListener.removeFrom(widget)
    clickListener.uninstall(widget)
  }

  override fun setUIDefaults(c: AbstractToolbarCombo) {
    super.setUIDefaults(c)
    val button = c as? ToolbarSplitButton ?: return
    button.separatorMargin = JBUI.CurrentTheme.MainToolbar.SplitDropdown.separatorMargin()
    button.leftPartMargin = JBUI.CurrentTheme.MainToolbar.SplitDropdown.leftPartMargin()
    button.rightPartMargin = JBUI.CurrentTheme.MainToolbar.SplitDropdown.rightPartMargin()
  }

  override fun paint(g: Graphics, c: JComponent) {
    val button = c as? ToolbarSplitButton ?: return

    paintBackground(g, button)

    val leftIcons = button.leftIcons
    val rightIcons = button.rightIcons
    val text = button.text
    assert(!StringUtil.isEmpty(text) || rightIcons.isEmpty()) { "Right icons are only allowed when text is not empty" }

    val innerRect = SwingUtilities.calculateInnerArea(c, null)
    val paintRect = Rectangle(innerRect)
    JBInsets.removeFrom(paintRect, button.leftPartMargin.getTopBottom())
    paintRect.cutLeft(c.leftPartMargin.left)
    val maxTextWidth = calcMaxTextWidth(button, innerRect)
    val g2 = g.create() as Graphics2D
    try {
      g2.clip(paintRect)
      GraphicsUtil.setupAAPainting(g2)

      //paint left icons
      if (!leftIcons.isEmpty()) {
        val iconsRect = paintIcons(leftIcons, button, g2, paintRect)
        paintRect.cutLeft(iconsRect.width)
      }

      ///paint text
      if (!StringUtil.isEmpty(text) && maxTextWidth > 0) {
        if (!leftIcons.isEmpty()) paintRect.cutLeft(button.iconTextGap)
        val textRect = Rectangle(paintRect.x, paintRect.y, maxTextWidth, paintRect.height)
        drawText(button, text!!, g2, textRect)
        paintRect.cutLeft(maxTextWidth)
      }

      //paint right icons
      if (!rightIcons.isEmpty()) {
        paintRect.cutLeft(button.iconTextGap)
        val iconsRect = paintIcons(rightIcons, button, g2, paintRect)
        paintRect.cutLeft(iconsRect.width)
      }

      //paint separator
      paintRect.cutLeft(button.leftPartMargin.right)
      JBInsets.addTo(paintRect, button.leftPartMargin.getTopBottom())
      val sMargin = button.separatorMargin
      val sHeight = paintRect.height - sMargin.top - sMargin.bottom
      g2.color = if (c.isEnabled()) UIManager.getColor("MainToolbar.separatorColor") else UIUtil.getLabelDisabledForeground()
      g2.fillRect(paintRect.x + sMargin.left, paintRect.y + sMargin.top, SEPARATOR_WIDTH, sHeight)
      paintRect.cutLeft(sMargin.left + sMargin.right + SEPARATOR_WIDTH)

      //pain expand button
      paintRect.cutLeft(button.rightPartMargin.left)
      JBInsets.removeFrom(paintRect, button.rightPartMargin.getTopBottom())
      paintIcons(listOf(AllIcons.General.ChevronDown), button, g2, paintRect)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val res = Dimension()
    val button = c as? ToolbarSplitButton ?: return res

    val leftIcons = button.leftIcons
    val rightIcons = button.rightIcons
    val lMargin = c.leftPartMargin
    val rMargin = c.rightPartMargin
    assert(!StringUtil.isEmpty(button.text) || rightIcons.isEmpty()) {"Right icons are only allowed when text is not empty"}

    if (!leftIcons.isEmpty()) {
      res.width += calcIconsWidth(leftIcons, button.betweenIconsGap)
      res.height = leftIcons.maxOf { it.iconHeight } + lMargin.top + lMargin.bottom
    }

    if (!StringUtil.isEmpty(button.text)) {
      if (!leftIcons.isEmpty()) res.width += button.iconTextGap
      val metrics = c.getFontMetrics(c.getFont())
      val text = getText(button)
      res.width += metrics.stringWidth(text!!)
      res.height = max(res.height, metrics.height)
    }

    if (!rightIcons.isEmpty()) {
      res.width += button.iconTextGap
      res.width += calcIconsWidth(rightIcons, button.betweenIconsGap)
      res.height = max(res.height, rightIcons.maxOf { it.iconHeight } + lMargin.top + lMargin.bottom)
    }

    res.width += lMargin.left + lMargin.right

    val sMargin = button.separatorMargin
    res.width += sMargin.left + SEPARATOR_WIDTH + sMargin.right

    res.width += rMargin.left + AllIcons.General.ChevronDown.iconWidth + rMargin.right
    res.height = max(res.height, AllIcons.General.ChevronDown.iconHeight + rMargin.top + rMargin.bottom)

    val insets = c.getInsets()

    res.height += insets.top + insets.bottom
    res.width += insets.left + insets.right

    return res
  }

  private fun calcMaxTextWidth(button: ToolbarSplitButton, innerRect: Rectangle): Int {
    var otherElementsWidth = 0

    var left = calcIconsWidth(button.leftIcons, button.betweenIconsGap)
    if (left > 0) left += button.iconTextGap
    otherElementsWidth += left

    var right = calcIconsWidth(button.rightIcons, button.betweenIconsGap)
    if (right > 0) right += button.iconTextGap
    otherElementsWidth += right

    val sMargin = button.separatorMargin
    otherElementsWidth += sMargin.left + SEPARATOR_WIDTH + sMargin.right + AllIcons.General.ChevronDown.iconWidth

    val lMargin = button.leftPartMargin
    val rMargin = button.rightPartMargin
    otherElementsWidth += lMargin.left + lMargin.right + rMargin.left + rMargin.right

    return innerRect.width - otherElementsWidth
  }

  private fun paintBackground(g: Graphics, button: ToolbarSplitButton) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

    try {
      if (button.isOpaque) {
        g2.color = button.background
        val bounds = g2.clipBounds
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)

      }

      if (button.isEnabled) {
        g2.color = if (ProjectWindowCustomizerService.getInstance().isActive()) button.transparentHoverBackground else button.hoverBackground
        val arc = JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().get()
        val (actionZone, expandZone) = getZones(button)
        if (button.model.isActionButtonSelected()) g2.fillRoundRect(actionZone.x, actionZone.y, actionZone.width, actionZone.height, arc, arc)
        if (button.model.isExpandButtonSelected()) g2.fillRoundRect(expandZone.x, expandZone.y, expandZone.width, expandZone.height, arc, arc)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun getZones(button: ToolbarSplitButton): Pair<Rectangle, Rectangle> {
    val insets = button.insets
    val separatorMargin = button.separatorMargin
    val rightPartMargin = button.rightPartMargin

    val zoneHeight = button.height - insets.top - insets.bottom
    val expandZoneStart = button.width - insets.right - rightPartMargin.right - AllIcons.General.ChevronDown.iconWidth - rightPartMargin.left
    val expandRect = Rectangle(expandZoneStart, insets.top,
                               AllIcons.General.ChevronDown.iconWidth + rightPartMargin.left + rightPartMargin.right, zoneHeight)

    val actionZoneWidth = button.width - expandRect.width - separatorMargin.right - SEPARATOR_WIDTH - separatorMargin.left - insets.left - insets.right
    val actionRect = Rectangle(insets.left, insets.top, actionZoneWidth, zoneHeight)

    return Pair(actionRect, expandRect)
  }

  private fun getZoneType(button: ToolbarSplitButton, point: Point): Int {
    val (action, expand) = getZones(button)
    return when {
      action.contains(point) -> ACTION_ZONE
      expand.contains(point) -> EXPAND_ZONE
      else -> DEAD_ZONE
    }
  }

  private fun Insets.getTopBottom() = Insets(top, 0, bottom, 0)

  private inner class MyClickListener: ClickListener() {
    override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
      (e.component as? ToolbarSplitButton)?.let { button ->
        if (button.isEnabled) {
          val ae = ActionEvent(button, 0, null, System.currentTimeMillis(), e.getModifiersEx())
          val zone = getZoneType(button, e.point)
          when(zone) {
            EXPAND_ZONE -> button.model.getExpandListeners().forEach { l -> l.actionPerformed(ae) }
            ACTION_ZONE -> button.model.getActionListeners().forEach { l -> l.actionPerformed(ae) }
          }
          return true
        }
      }
      return false
    }


  }

  private inner class MyHoverListener: HoverListener() {
    override fun mouseEntered(c: Component, x: Int, y: Int) = updateSelectedZone(c, x, y)

    override fun mouseMoved(c: Component, x: Int, y: Int)  = updateSelectedZone(c, x, y)

    override fun mouseExited(c: Component) {
      (c as? ToolbarSplitButton)?.model?.let {
        it.setActionButtonSelected(false)
        it.setExpandButtonSelected(false)
      }
    }

    private fun updateSelectedZone(c: Component, x: Int, y: Int) {
      val button = c as? ToolbarSplitButton ?: return
      val zone = getZoneType(button, Point(x, y))
      button.model.setActionButtonSelected(zone == ACTION_ZONE)
      button.model.setExpandButtonSelected(zone == EXPAND_ZONE)
    }
  }
}
