// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.max
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicToggleButtonUI

/**
 * Islands-themed on/off toggle UI delegate.
 *
 * Renders a pill-shaped track with manually painted notch indicators (no text labels).
 * Supports default, disabled, and focused states via [javax.swing.ButtonModel].
 *
 * Registered in Islands theme JSON via `"OnOffButtonUI"` key.
 */
@Suppress("unused")
internal class IslandsOnOffButtonUI : BasicToggleButtonUI() {

  companion object {
    private val ON_BG = JBColor.namedColor("ToggleButton.onBackground", 0x3871E1)

    private val OFF_BG = JBColor.namedColor("ToggleButton.offBackground", JBColor(Color(0x00, 0x00, 0x00, 0x45), Color(0xFF, 0xFF, 0xFF, 0x29)))

    private val TRANSPARENT = JBColor(Color(0x00000000, true), Color(0x00000000, true))

    private val ON_DISABLED_BACKGROUND = JBColor.namedColor("ToggleButton.onDisabledBackground", TRANSPARENT)
    private val OFF_DISABLED_BACKGROUND = JBColor.namedColor("ToggleButton.offDisabledBackground", TRANSPARENT)

    private val DISABLED_BORDER_FALLBACK = JBColor(0xDDDFE4, 0x33353B)
    private val ON_DISABLED_BORDER = JBColor.namedColor("ToggleButton.onDisabledBorderColor", DISABLED_BORDER_FALLBACK)
    private val OFF_DISABLED_BORDER = JBColor.namedColor("ToggleButton.offDisabledBorderColor", DISABLED_BORDER_FALLBACK)

    private val FOCUS_BORDER = JBColor.namedColor("ToggleButton.focusBorderColor", ON_BG)

    private val ON_NOTCH_COLOR = JBColor.namedColor("ToggleButton.onNotchColor", JBColor(Color.WHITE, Color.WHITE))
    private val OFF_NOTCH_COLOR = JBColor.namedColor("ToggleButton.offNotchColor", JBColor(Color.WHITE, Color.WHITE))
    private val ON_DISABLED_NOTCH_COLOR = JBColor.namedColor("ToggleButton.onDisabledNotchColor", JBColor(0xC3C5CB, 0x5F6269))
    private val OFF_DISABLED_NOTCH_COLOR = JBColor.namedColor("ToggleButton.offDisabledNotchColor", JBColor(0xC3C5CB, 0x5F6269))

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): IslandsOnOffButtonUI {
      c.alignmentY = 0.5f
      (c as? AbstractButton)?.isRolloverEnabled = true
      return IslandsOnOffButtonUI()
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

    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

      val selected = c.isSelected
      val enabled = c.isEnabled

      val w = JBUIScale.scale(26f)
      val h = JBUIScale.scale(16f)
      val x = (c.width - w) / 2f
      val y = (c.height - h) / 2f
      val arc = h

      val track = RoundRectangle2D.Float(x, y, w, h, arc, arc)

      if (!enabled) {
        val trackFill = if (selected) ON_DISABLED_BACKGROUND else OFF_DISABLED_BACKGROUND
        val borderColor = if (selected) ON_DISABLED_BORDER else OFF_DISABLED_BORDER
        paintDisabledTrack(g2, track, arc, trackFill, borderColor)
        g2.color = if (selected) ON_DISABLED_NOTCH_COLOR else OFF_DISABLED_NOTCH_COLOR
        if (selected) paintOnNotch(g2, x, y, h) else paintOffNotch(g2, x, y, h)
      }
      else {
        g2.color = if (selected) ON_BG else OFF_BG
        g2.fill(track)
        g2.color = if (selected) ON_NOTCH_COLOR else OFF_NOTCH_COLOR
        if (selected) paintOnNotch(g2, x, y, h) else paintOffNotch(g2, x, y, h)
      }

      if (enabled && DarculaUIUtil.getOutline(c) == null && c.hasFocus()) {
        paintFocusRing(g2, x, y, w, h)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintOnNotch(g2: Graphics2D, x: Float, y: Float, h: Float) {
    val nd = JBUIScale.scale(10f)
    val ny = y + (h - nd) / 2f
    val nx = x + JBUIScale.scale(13f)
    g2.fill(Ellipse2D.Float(nx, ny, nd, nd))
  }

  private fun paintOffNotch(g2: Graphics2D, x: Float, y: Float, h: Float) {
    val nd = JBUIScale.scale(9f)
    val ny = y + (h - nd) / 2f
    val nx = x + JBUIScale.scale(3.5f)
    val outer = Area(Ellipse2D.Float(nx, ny, nd, nd))
    val holeSize = JBUIScale.scale(5f)
    val hx = nx + (nd - holeSize) / 2f
    val hy = ny + (nd - holeSize) / 2f
    outer.subtract(Area(Ellipse2D.Float(hx, hy, holeSize, holeSize)))
    g2.fill(outer)
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

    val g2s = g2.create() as Graphics2D
    try {
      g2s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2s.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      val outline = Path2D.Float(Path2D.WIND_EVEN_ODD)
      outline.append(outer, false)
      outline.append(inner, false)
      g2s.color = FOCUS_BORDER
      g2s.fill(outline)
    }
    finally {
      g2s.dispose()
    }
  }

  /**
   * Disabled track: Figma uses `border` (1px) on the pill + `control-bg-disabled` fill (often transparent on dark).
   */
  private fun paintDisabledTrack(
    g2: Graphics2D,
    track: RoundRectangle2D.Float,
    arc: Float,
    trackFill: Color,
    borderColor: Color,
  ) {
    if (trackFill.alpha > 0) {
      g2.color = trackFill
      g2.fill(track)
    }

    val sw = JBUIScale.scale(1f)
    g2.color = borderColor
    val innerArc = (arc - sw * 2f).coerceAtLeast(0f)
    val outer = Area(track)
    outer.subtract(Area(RoundRectangle2D.Float(
      track.x + sw, track.y + sw,
      track.width - sw * 2f, track.height - sw * 2f,
      innerArc, innerArc,
    )))
    g2.fill(outer)
  }
}
