// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

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
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.plaf.basic.BasicToggleButtonUI

/**
 * Islands-themed on/off toggle UI delegate.
 *
 * Renders a pill-shaped track with a small notch indicator (no text labels).
 * Supports default and disabled states via [javax.swing.ButtonModel].
 *
 * Registered in Islands theme JSON via `"OnOffButtonUI"` key.
 */
@Suppress("unused")
internal class IslandsOnOffButtonUI : BasicToggleButtonUI() {

  companion object {
    private val ON_BG = JBColor.namedColor("ToggleButton.onBackground", 0x3871E1)

    private val OFF_BG = JBColor.namedColor("ToggleButton.offBackground", JBColor(Color(0x00, 0x00, 0x00, 0x45), Color(0xFF, 0xFF, 0xFF, 0x29)))

    private val NOTCH_COLOR = JBColor.namedColor("ToggleButton.buttonColor", JBColor(Color.WHITE, Color.WHITE))

    private val DISABLED_TRACK_FILL = JBColor.namedColor(
      "ToggleButton.disabledTrackFill",
      JBColor(Color(0x00000000, true), Color(0x00000000, true)),
    )
    private val DISABLED_BORDER = JBColor.namedColor(
      "ToggleButton.disabledBorderColor",
      JBColor(0xDDDFE4, 0x33353B),
    )
    private val DISABLED_NOTCH_COLOR = JBColor.namedColor(
      "ToggleButton.disabledButtonColor",
      JBColor(0xC3C5CB, 0x5F6269),
    )

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): IslandsOnOffButtonUI {
      c.alignmentY = 0.5f
      (c as? AbstractButton)?.isRolloverEnabled = true
      return IslandsOnOffButtonUI()
    }
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    return Dimension(JBUIScale.scale(26), JBUIScale.scale(16))
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
        paintDisabledTrack(g2, track, arc)
        g2.color = DISABLED_NOTCH_COLOR
        if (selected) paintOnNotch(g2, x, y, h)
        else paintOffNotch(g2, x, y, h)
      }
      else {
        g2.color = if (selected) ON_BG else OFF_BG
        g2.fill(track)

        g2.color = NOTCH_COLOR
        if (selected) paintOnNotch(g2, x, y, h)
        else paintOffNotch(g2, x, y, h)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintDisabledTrack(g2: Graphics2D, track: RoundRectangle2D.Float, arc: Float) {
    val fill = DISABLED_TRACK_FILL
    if (fill.alpha > 0) {
      g2.color = fill
      g2.fill(track)
    }

    val sw = JBUIScale.scale(1f)
    g2.color = DISABLED_BORDER
    val innerArc = (arc - sw * 2f).coerceAtLeast(0f)
    val outer = Area(track)
    outer.subtract(Area(RoundRectangle2D.Float(
      track.x + sw, track.y + sw,
      track.width - sw * 2f, track.height - sw * 2f,
      innerArc, innerArc,
    )))
    g2.fill(outer)
  }

  private fun paintOnNotch(g2: Graphics2D, x: Float, y: Float, h: Float) {
    val nh = JBUIScale.scale(7f)
    val nw = JBUIScale.scale(2f)
    val nx = x + JBUIScale.scale(18f)
    val ny = y + (h - nh) / 2f
    g2.fill(RoundRectangle2D.Float(nx, ny, nw, nh, nw, nw))
  }

  private fun paintOffNotch(g2: Graphics2D, x: Float, y: Float, h: Float) {
    val nd = JBUIScale.scale(8f)
    val ny = y + (h - nd) / 2f
    val nx = x + JBUIScale.scale(4f)
    val outer = Area(Ellipse2D.Float(nx, ny, nd, nd))
    val holeSize = JBUIScale.scale(4f)
    val hx = nx + (nd - holeSize) / 2f
    val hy = ny + (nd - holeSize) / 2f
    outer.subtract(Area(Ellipse2D.Float(hx, hy, holeSize, holeSize)))
    g2.fill(outer)
  }
}
