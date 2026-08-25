// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.ui.JBColor
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicToggleButtonUI
import kotlin.math.max

private const val ICONS_DIR = "com/intellij/ide/ui/laf/icons/"

/**
 * Islands-themed on/off toggle UI delegate.
 *
 * Renders a pill-shaped track with a notch indicator (no text labels) from `toggle*.svg` icons.
 * Track, border and notch colors come from the `Toggle.*` theme palette keys, which are applied
 * to the SVG elements by id (see `UiThemePaletteCheckBoxScope`), so a single asset per state
 * serves every theme. Supports default, disabled, and focused states via [javax.swing.ButtonModel].
 *
 * Registered in Islands theme JSON via `"OnOffButtonUI"` key.
 */
@Suppress("unused")
internal class IslandsOnOffButtonUI : BasicToggleButtonUI() {

  companion object {
    private val FOCUS_BORDER = JBColor.namedColor("ToggleButton.focusBorderColor", 0x3871E1)

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): IslandsOnOffButtonUI {
      return IslandsOnOffButtonUI()
    }

    private fun getIcon(selected: Boolean, enabled: Boolean): Icon? {
      val name = (if (selected) "toggleOn" else "toggleOff") + (if (enabled) "" else "Disabled")
      return findIconUsingNewImplementation(path = "$ICONS_DIR$name.svg", classLoader = IslandsOnOffButtonUI::class.java.classLoader)
    }
  }

  /**
   * Space from the track outer edge to the outer edge of the focus ring (gap + stroke),
   * plus extra room so AA is not clipped — same stacking as [DarculaCheckBoxUI] validation outline
   * (outer rect minus inner rect, WIND_EVEN_ODD fill).
   */
  private fun focusOuterExtentPx(): Int {
    val gap = JBUIScale.scale(1f)
    val stroke = JBUIScale.scale(2f)
    val safe = JBUIScale.scale(1f)
    return (gap + stroke + safe).toInt()
  }

  override fun installUI(c: JComponent) {
    super.installUI(c)
    c.alignmentY = 0.5f
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val trackW = JBUIScale.scale(26)
    val trackH = JBUIScale.scale(16)
    val pad = focusOuterExtentPx()
    val w = trackW + 2 * pad
    val h = max(JBUIScale.scale(32), trackH + 2 * pad)
    return Dimension(w, h)
  }

  override fun getMinimumSize(c: JComponent): Dimension = getPreferredSize(c)

  override fun getMaximumSize(c: JComponent): Dimension = getPreferredSize(c)

  override fun paint(g: Graphics, c: JComponent) {
    if (c !is OnOffButton) return

    val icon = getIcon(selected = c.isSelected, enabled = c.isEnabled) ?: return

    val x = (c.width - icon.iconWidth) / 2
    val y = (c.height - icon.iconHeight) / 2
    icon.paintIcon(c, g, x, y)

    if (c.isEnabled && DarculaUIUtil.getOutline(c) == null && c.hasFocus()) {
      val g2 = g.create() as Graphics2D
      try {
        paintFocusRing(g2, x.toFloat(), y.toFloat(), icon.iconWidth.toFloat(), icon.iconHeight.toFloat())
      }
      finally {
        g2.dispose()
      }
    }
  }

  /**
   * Focus ring: same idea as [DarculaCheckBoxUI.drawCheckIcon] validation outline — two concentric
   * round rects, [Path2D.WIND_EVEN_ODD] fill — uniform **gap** (track → inner edge of ring) and
   * uniform **stroke** thickness (ring width). A single stroked path centers the stroke on one
   * offset curve and reads uneven vs the checkbox SVG / outline fill.
   */
  private fun paintFocusRing(g2: Graphics2D, trackX: Float, trackY: Float, trackW: Float, trackH: Float) {
    val gap = JBUIScale.scale(1f)
    val stroke = JBUIScale.scale(2f)
    val outerInset = gap + stroke
    val innerInset = gap

    val oh = trackH + 2f * outerInset
    val ow = trackW + 2f * outerInset
    val outer = RoundRectangle2D.Float(trackX - outerInset, trackY - outerInset, ow, oh, oh, oh)

    val ih = trackH + 2f * innerInset
    val iw = trackW + 2f * innerInset
    val inner = RoundRectangle2D.Float(trackX - innerInset, trackY - innerInset, iw, ih, ih, ih)

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
    val outline = Path2D.Float(Path2D.WIND_EVEN_ODD)
    outline.append(outer, false)
    outline.append(inner, false)
    g2.color = FOCUS_BORDER
    g2.fill(outline)
  }
}
