// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.GeneralPath
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.LookAndFeel
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicSliderUI

public open class DarculaSliderUI(b: JComponent? = null) : BasicSliderUI(b as JSlider) {
  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): DarculaSliderUI = DarculaSliderUI(c)
  }

  private val theme = DarculaSliderUIThemes()

  override fun paintThumb(g: Graphics) {
    val g2d = g.create() as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    try {
      val path = if (slider.orientation == SwingConstants.HORIZONTAL) {
        val x1 = thumbRect.x + theme.focusBorderThickness
        val x2 = x1 + thumbRect.width - (theme.focusBorderThickness * 2)
        val x3 = theme.thumbHalfWidth + 1 + x1
        val y1 = thumbRect.y + theme.focusBorderThickness
        val y2 = y1 + theme.thumbOverhang
        val y3 = y1 + thumbRect.height - (theme.focusBorderThickness * 2)
        getHPath(x1, y1, x2, y2, x3, y3)
      }
      else {
        val x1 = thumbRect.x + theme.focusBorderThickness
        val x2 = x1 + theme.thumbOverhang
        val x3 = x1 + thumbRect.width - (theme.focusBorderThickness * 2)
        val y1 = thumbRect.y + theme.focusBorderThickness
        val y2 = y1 + theme.thumbHalfWidth + 1
        val y3 = y1 + thumbRect.height - (theme.focusBorderThickness * 2)
        getVPath(x1, y1, x2, x3, y2, y3)
      }

      if (slider.hasFocus()) {
        g2d.stroke = BasicStroke((theme.focusBorderThickness + theme.borderThickness).toFloat())
        g2d.paint = theme.focusedOuterColor
        g2d.draw(path)
      }

      g2d.paint = if (slider.isEnabled) theme.buttonColor else theme.disabledButtonColor
      g2d.fill(path)

      g2d.paint = if (slider.hasFocus()) {
        theme.focusedBorderColor
      }
      else if (slider.isEnabled) {
        theme.buttonBorderColor
      }
      else {
        theme.disabledButtonBorderColor
      }

      g2d.stroke = BasicStroke(theme.borderThickness.toFloat())
      g2d.draw(path)
    }
    finally {
      g2d.dispose()
    }
  }

  private fun getHPath(x1: Int,
                       y1: Int,
                       x2: Int,
                       y2: Int,
                       x3: Int,
                       y3: Int): GeneralPath {
    val path = GeneralPath()
    path.moveTo((x1 + theme.arc).toDouble(), y1.toDouble())
    path.lineTo((x2 - theme.arc).toDouble(), y1.toDouble())
    path.lineTo(x2.toDouble(), (y1 + theme.arc).toDouble())
    // path.quadTo(path.currentPoint.x, path.currentPoint.y, x2.toDouble(), (y1 + arc).toDouble())
    path.lineTo(x2.toDouble(), y2.toDouble())
    path.lineTo(x3.toDouble(), y3.toDouble())
    path.lineTo(x1.toDouble(), y2.toDouble())
    path.lineTo(x1.toDouble(), (y1 + theme.arc).toDouble())
    path.lineTo(x1 + theme.arc.toDouble(), y1.toDouble())
    //  path.quadTo(path.currentPoint.x, path.currentPoint.y, x1 + arc.toDouble(), y1.toDouble())
    path.closePath()
    return path
  }

  private fun getVPath(x1: Int,
                       y1: Int,
                       x2: Int,
                       x3: Int,
                       y2: Int,
                       y3: Int): GeneralPath {
    val path = GeneralPath()
    path.moveTo((x1 + theme.arc).toDouble(), y1.toDouble())
    path.lineTo(x2.toDouble(), y1.toDouble())
    path.lineTo(x3.toDouble(), y2.toDouble())
    path.lineTo(x2.toDouble(), y3.toDouble())
    path.lineTo((x1 + theme.arc).toDouble(), y3.toDouble())
    path.lineTo(x1.toDouble(), (y3 - theme.arc).toDouble())
    // path.quadTo(path.currentPoint.x, path.currentPoint.y, x1.toDouble(), (y3 - arc).toDouble())
    path.lineTo(x1.toDouble(), (y1 + theme.arc).toDouble())
    path.lineTo((x1 + theme.arc).toDouble(), y1.toDouble())
    //path.quadTo(path.currentPoint.x, path.currentPoint.y, (x1 + arc).toDouble(), y1.toDouble())
    path.closePath()
    return path
  }

  override fun calculateThumbLocation() {
    super.calculateThumbLocation()
    if (slider.orientation == JSlider.HORIZONTAL) {
      val valuePosition = xPositionForValue(slider.value)
      thumbRect.x = valuePosition - theme.focusedThumbHalfWidth
      thumbRect.y = trackRect.y
    }
    else {
      val valuePosition = yPositionForValue(slider.value)
      thumbRect.x = trackRect.x
      thumbRect.y = valuePosition - theme.focusedThumbHalfWidth
    }
  }

  override fun setThumbLocation(x: Int, y: Int) {
    super.setThumbLocation(x, y)
    slider.repaint()
  }

  override fun getBaseline(c: JComponent?, width: Int, height: Int): Int {
    if (slider.orientation == SwingConstants.HORIZONTAL) {
      return theme.thumbOverhang + (2 * theme.focusBorderThickness) + slider.insets.top + theme.borderThickness
    }
    else {
      return super.getBaseline(c, width, height)
    }
  }

  override fun paintTrack(g: Graphics) {
    val g2d = g.create() as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.paint = if (slider.isEnabled) theme.trackColor else theme.disabledTrackColor
    try {
      if (slider.orientation == SwingConstants.HORIZONTAL) {
        val y = thumbRect.y + theme.focusBorderThickness + theme.thumbOverhang - theme.trackThickness
        LinePainter2D.paint(g2d, trackRect.getX(), y.toDouble(), trackRect.maxX, y.toDouble(),
                            LinePainter2D.StrokeType.INSIDE,
                            theme.trackThickness.toDouble())
      }
      else {
        val x = thumbRect.x + theme.focusBorderThickness + theme.thumbOverhang - theme.trackThickness
        LinePainter2D.paint(g2d, x.toDouble(), trackRect.y.toDouble(), x.toDouble(), trackRect.maxY, LinePainter2D.StrokeType.INSIDE,
                            theme.trackThickness.toDouble())
      }
    }
    finally {
      g2d.dispose()
    }
  }

  override fun paintFocus(g: Graphics) {
    paintThumb(g)
  }

  override fun getThumbSize(): Dimension {
    val width = (theme.focusedThumbHalfWidth * 2) + 1
    return if (slider.orientation == SwingConstants.HORIZONTAL) Dimension(width, theme.thumbHeight)
    else Dimension(theme.thumbHeight, width)
  }

  override fun calculateTrackBuffer() {
    if (slider.paintLabels && slider.labelTable != null) {
      val highLabel = highestValueLabel
      val lowLabel = lowestValueLabel
      if (slider.orientation == JSlider.HORIZONTAL) {
        if (highLabel != null && lowLabel != null) {
          trackBuffer = highLabel.bounds.width.coerceAtLeast(lowLabel.bounds.width) / 2
        }
        trackBuffer = trackBuffer.coerceAtLeast(theme.focusedThumbHalfWidth)
      }
      else {
        if (highLabel != null && lowLabel != null) {
          trackBuffer = highLabel.bounds.height.coerceAtLeast(lowLabel.bounds.height) / 2
        }
        trackBuffer = trackBuffer.coerceAtLeast(theme.focusedThumbHalfWidth) + 1
      }
    }
    else {
      trackBuffer = theme.focusedThumbHalfWidth + 1
    }
  }

  override fun calculateLabelRect() {
    super.calculateLabelRect()

    if (slider.orientation == JSlider.HORIZONTAL) {
      labelRect.y += theme.tickBottom
    }
    else {
      labelRect.x += theme.tickBottom
    }
  }

  override fun calculateTickRect() {
    if (slider.orientation == JSlider.HORIZONTAL) {
      tickRect.x = trackRect.x
      tickRect.y = trackRect.y + theme.focusBorderThickness + theme.thumbOverhang + theme.tickTop
      tickRect.width = trackRect.width
      tickRect.height = if (slider.paintTicks) tickLength else 0
    }
    else {
      tickRect.width = if (slider.paintTicks) tickLength else 0
      if (slider.componentOrientation.isLeftToRight) {
        tickRect.x = trackRect.x + theme.focusBorderThickness + theme.thumbOverhang + theme.tickTop
      }
      else {
        tickRect.x = trackRect.x - (theme.focusBorderThickness + theme.thumbOverhang + theme.tickTop)
      }
      tickRect.y = trackRect.y
      tickRect.height = trackRect.height
    }
  }

  override fun getTickLength(): Int {
    return theme.majorTickHeight
  }

  override fun installDefaults(slider: JSlider?) {
    LookAndFeel.installColorsAndFont(slider, "Slider.background",
                                     "Slider.foreground", "Slider.font")
    focusInsets = JBInsets.emptyInsets()
  }

  override fun paintMinorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x_: Int) {
    g as Graphics2D
    g.color = if (slider.isEnabled) theme.tickColor else theme.disabledTickColor
    val x = x_ + 1
    g.stroke = BasicStroke(theme.borderThickness.toFloat())
    g.drawLine(x, 0, x, theme.minorTickHeight)
  }

  override fun paintMajorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x_: Int) {
    g as Graphics2D
    g.color = if (slider.isEnabled) theme.tickColor else theme.disabledTickColor
    val x = x_ + 1
    g.stroke = BasicStroke(theme.borderThickness.toFloat())
    g.drawLine(x, 0, x, theme.majorTickHeight)
  }

  override fun paintMinorTickForVertSlider(g: Graphics, tickBounds: Rectangle, y_: Int) {
    g as Graphics2D
    g.color = if (slider.isEnabled) theme.tickColor else theme.disabledTickColor
    val y = y_ + 1
    g.stroke = BasicStroke(theme.borderThickness.toFloat())
    g.drawLine(0, y, theme.minorTickHeight, y)
  }

  override fun paintMajorTickForVertSlider(g: Graphics, tickBounds: Rectangle, y_: Int) {
    g as Graphics2D
    g.color = if (slider.isEnabled) theme.tickColor else theme.disabledTickColor
    val y = y_ + 1
    g.stroke = BasicStroke(theme.borderThickness.toFloat())
    g.drawLine(0, y, theme.majorTickHeight, y)
  }

}

public class DarculaSliderUIThemes {
  val thumbHalfWidth = JBUI.scale(7)
  val thumbHeight = JBUI.scale(24)
  val focusedThumbHalfWidth = thumbHalfWidth + focusBorderThickness
  val arc: Int
    get() = JBUI.scale(1)
  val trackThickness: Int
    get() = JBUI.scale(3)
  val focusBorderThickness: Int
    get() = JBUI.scale(3)
  val focusedBorderColor: Color
    get() = JBUI.CurrentTheme.Focus.focusColor()
  val borderThickness: Int
    get() = JBUI.scale(1)
  val thumbOverhang: Int
    get() = JBUI.scale(10)
  val buttonColor: Color
    get() = JBColor.namedColor("Slider.buttonColor", JBColor(0xFFFFFF, 0x9B9E9E))
  val buttonBorderColor: Color
    get() = JBColor.namedColor("Slider.buttonBorderColor", JBColor(0xA6A6A6, 0x393D3F))
  val trackColor: Color
    get() = JBColor.namedColor("Slider.trackColor", JBColor(0xC7C7C7, 0x666666))
  val tickColor: Color
    get() = JBColor.namedColor("Slider.tickColor", JBColor(0x999999, 0x808080))
  val focusedOuterColor: Color
    get() = JBUI.CurrentTheme.Component.FOCUSED_BORDER_COLOR
  val disabledButtonColor: Color
    get() = JBColor.PanelBackground
  val disabledButtonBorderColor: Color
    get() = JBColor.namedColor("Component.disabledBorderColor", 0x87AFDA)
  val disabledTrackColor: Color
    get() = disabledButtonBorderColor
  val disabledTickColor: Color
    get() = disabledButtonBorderColor
  val tickTop: Int
    get() = JBUI.scale(6)
  val tickBottom: Int
    get() = JBUI.scale(2)
  val minorTickHeight: Int
    get() = JBUI.scale(4)
  val majorTickHeight: Int
    get() = JBUI.scale(7)
}