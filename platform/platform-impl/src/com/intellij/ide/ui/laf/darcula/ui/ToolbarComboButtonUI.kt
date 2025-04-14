// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.AbstractToolbarCombo
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.ClickListener
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.max

private const val BEFORE_CHEVRON_GAP = 2

internal class ToolbarComboButtonUI: AbstractToolbarComboUI() {
  private val clickListener = MyClickListener()
  private val hoverListener = MyHoverListener()

  companion object {
    // JvmStatic for Swing - used as a component UI.
    @JvmStatic
    fun createUI(@Suppress("UNUSED_PARAMETER") ignored: JComponent): ToolbarComboButtonUI = ToolbarComboButtonUI()
  }

  override fun setUIDefaults(c: AbstractToolbarCombo) {
    super.setUIDefaults(c)
    (c as? ToolbarComboButton)?.margin = JBUI.CurrentTheme.MainToolbar.Dropdown.margin()
  }

  override fun installUI(c: JComponent) {
    val widget = c as ToolbarComboButton
    setUIDefaults(widget)
    widget.addPropertyChangeListener(this)
    tryUpdateHtmlRenderer(widget, widget.text)
    hoverListener.addTo(widget)
    clickListener.installOn(widget)
  }

  override fun uninstallUI(c: JComponent) {
    val widget = c as ToolbarComboButton
    widget.removePropertyChangeListener(this)
    tryUpdateHtmlRenderer(widget, "")
    hoverListener.removeFrom(widget)
    clickListener.uninstall(widget)
  }

  override fun paint(g: Graphics, c: JComponent) {
    val combo = c as? ToolbarComboButton ?: return
    paintBackground(g, combo)

    val leftIcons = combo.leftIcons
    val rightIcons = combo.rightIcons
    val text = combo.text
    assert(!StringUtil.isEmpty(text) || rightIcons.isEmpty()) { "Right icons are only allowed when text is not empty" }

    val innerRect = SwingUtilities.calculateInnerArea(c, null)
    val paintRect = Rectangle(innerRect)
    JBInsets.removeFrom(paintRect, c.margin)
    val maxTextWidth = calcMaxTextWidth(combo, paintRect)
    val g2 = g.create() as Graphics2D
    try {
      g2.clip(paintRect)
      GraphicsUtil.setupAAPainting(g2)
      if (!leftIcons.isEmpty()) {
        val iconsRect = paintIcons(leftIcons, combo, g2, paintRect)
        paintRect.cutLeft(iconsRect.width)
      }

      if (!text.isNullOrEmpty() && maxTextWidth > 0) {
        if (!leftIcons.isEmpty()) paintRect.cutLeft(ICON_TEXT_GAP)
        val textRect = Rectangle(paintRect.x, paintRect.y, maxTextWidth, paintRect.height)
        drawText(c, text, g2, textRect)
        paintRect.cutLeft(maxTextWidth)
      }

      if (!rightIcons.isEmpty()) {
        paintRect.cutLeft(ICON_TEXT_GAP)
        val iconsRect = paintIcons(rightIcons, combo, g2, paintRect)
        paintRect.cutLeft(iconsRect.width)
      }

      paintRect.cutLeft(BEFORE_CHEVRON_GAP)
      paintIcons(listOf(AllIcons.General.ChevronDown), combo, g2, paintRect)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val combo = c as ToolbarComboButton
    val result = Dimension()

    val leftIcons = combo.leftIcons
    val rightIcons = combo.rightIcons
    val text = combo.text
    assert(!text.isNullOrEmpty() || rightIcons.isEmpty()) {
      "Right icons are only allowed when text is not empty"
    }

    if (!leftIcons.isEmpty()) {
      result.width += calcIconsWidth(leftIcons, BETWEEN_ICONS_GAP)
      result.height = leftIcons.stream().mapToInt{ it.iconHeight }.max().orElse(0)
    }

    if (!combo.text.isNullOrEmpty()) {
      if (!leftIcons.isEmpty()) {
        result.width += ICON_TEXT_GAP
      }
      val metrics = c.getFontMetrics(c.getFont())
      val text = getText(combo)
      result.width += UIUtil.computeStringWidth(c, metrics, text)
      result.height = max(result.height, metrics.height)
    }

    if (!rightIcons.isEmpty()) {
      result.width += ICON_TEXT_GAP
      result.width += calcIconsWidth(rightIcons, BETWEEN_ICONS_GAP)
      result.height = max(result.height, rightIcons.stream().mapToInt{ it.iconHeight }.max().orElse(0))
    }

    result.width += BEFORE_CHEVRON_GAP + AllIcons.General.ChevronDown.iconWidth
    result.height = max(result.height, AllIcons.General.ChevronDown.iconHeight)

    val insets = c.getInsets()
    val margin = c.margin
    val heightSupplier = combo.preferredHeightSupplier
    if (heightSupplier != null) {
      result.height = result.height.coerceAtLeast(heightSupplier() - margin.top - margin.bottom)
    }
    result.height += insets.top + insets.bottom + margin.top + margin.bottom
    result.width += insets.left + insets.right + margin.left + margin.right

    return result
  }

  private fun calcMaxTextWidth(c: ToolbarComboButton, paintRect: Rectangle): Int {
    var otherElementsWidth = 0

    var left = calcIconsWidth(c.leftIcons, BETWEEN_ICONS_GAP)
    if (left > 0) left += ICON_TEXT_GAP
    otherElementsWidth += left

    var right = calcIconsWidth(c.rightIcons, BETWEEN_ICONS_GAP)
    if (right > 0) right += ICON_TEXT_GAP
    otherElementsWidth += right

    otherElementsWidth += BEFORE_CHEVRON_GAP + AllIcons.General.ChevronDown.iconWidth

    return paintRect.width - otherElementsWidth
  }

  private fun paintBackground(g: Graphics, combo: ToolbarComboButton) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

    try {
      if (combo.isOpaque) {
        g2.color = combo.background
        val bounds = g2.clipBounds
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
      }

      val innerRect = SwingUtilities.calculateInnerArea(combo, null)
      val arc = JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().get()
      val innerRectPainter: (Color) -> Unit = { bg ->
        g2.color = bg
        g2.fillRoundRect(innerRect.x, innerRect.y, innerRect.width, innerRect.height, arc, arc)
      }
      combo.highlightBackground?.let(innerRectPainter)

      if (combo.isEnabled && combo.model.isSelected()) {
        val hoverBackground = if (ProjectWindowCustomizerService.getInstance().isActive()) combo.transparentHoverBackground else combo.hoverBackground
        hoverBackground?.let(innerRectPainter)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private class MyClickListener: ClickListener() {
    override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
      (e.component as? ToolbarComboButton)?.let { combo ->
        if (combo.isEnabled) {
          val ae = ActionEvent(combo, 0, null, System.currentTimeMillis(), e.getModifiersEx())
          combo.model.getActionListeners().forEach { listener: ActionListener -> listener.actionPerformed(ae) }
          return true
        }
      }
      return false
    }
  }

  private class MyHoverListener: HoverListener() {
    override fun mouseEntered(c: Component, x: Int, y: Int) {
      (c as? ToolbarComboButton)?.model?.setSelected(true)
    }

    override fun mouseExited(c: Component) {
      (c as? ToolbarComboButton)?.model?.setSelected(false)
    }

    override fun mouseMoved(c: Component, x: Int, y: Int) {}
  }
}
