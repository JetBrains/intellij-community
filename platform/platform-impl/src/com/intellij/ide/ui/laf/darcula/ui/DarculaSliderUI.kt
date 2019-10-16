// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Area
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

  override fun paintThumb(g: Graphics) {
    val g2d = g.create() as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    try {
      val path = if (slider.orientation == SwingConstants.HORIZONTAL) {
        val x1 = thumbRect.x + focusBorderThickness
        val x2 = x1 + thumbRect.width - (focusBorderThickness * 2)
        val x3 = thumbHalfWidth + 1 + x1
        val y1 = thumbRect.y + focusBorderThickness
        val y2 = y1 + thumbOverhang
        val y3 = y1 + thumbRect.height - (focusBorderThickness * 2)
        getHPath(x1, y1, x2, y2, x3, y3)
      }
      else {
        val x1 = thumbRect.x + focusBorderThickness
        val x2 = x1 + thumbOverhang
        val x3 = x1 + thumbRect.width - (focusBorderThickness * 2)
        val y1 = thumbRect.y + focusBorderThickness
        val y2 = y1 + thumbHalfWidth + 1
        val y3 = y1 + thumbRect.height - (focusBorderThickness * 2)
        getVPath(x1, y1, x2, x3, y2, y3)
      }

      val clip = if (slider.orientation == SwingConstants.HORIZONTAL) {
        val x1 = thumbRect.x
        val x2 = x1 + thumbRect.width
        val x3 = thumbHalfWidth + 1 + x1
        val y1 = thumbRect.y
        val y2 = y1 + thumbOverhang
        val y3 = y1 + thumbRect.height
        val area = Area(getHPath(x1, y1, x2, y2, x3, y3))
        area.add(Area(Rectangle(x1, y2, x2, y3)))
        area
      }
      else {
        val x1 = thumbRect.x
        val x2 = x1 + thumbOverhang
        val x3 = x1 + thumbRect.width
        val y1 = thumbRect.y
        val y2 = y1 + thumbHalfWidth + 1
        val y3 = y1 + thumbRect.height
        val area = Area(getVPath(x1, y1, x2, x3, y2, y3))
        area.add(Area(Rectangle(x2, y2, x3, y3)))
        area
      }

      // g2d.clip(clip)

      if (slider.hasFocus()) {
        g2d.stroke = BasicStroke((focusBorderThickness + borderThickness).toFloat())
        g2d.paint = focusedOuterColor
        g2d.draw(path)
      }

      g2d.paint = if (slider.isEnabled) buttonColor else disabledButtonColor
      g2d.fill(path)

      g2d.paint = if (slider.hasFocus()) {
        focusedBorderColor
      }
      else if (slider.isEnabled) {
        buttonBorderColor
      }
      else {
        disabledButtonColor
      }

      g2d.stroke = BasicStroke(borderThickness.toFloat())
      g2d.paint = if (slider.isEnabled) buttonBorderColor else disabledButtonBorderColor
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
    path.moveTo((x1 + arc).toDouble(), y1.toDouble())
    path.lineTo((x2 - arc).toDouble(), y1.toDouble())
    path.lineTo(x2.toDouble(), (y1 + arc).toDouble())
    // path.quadTo(path.currentPoint.x, path.currentPoint.y, x2.toDouble(), (y1 + arc).toDouble())
    path.lineTo(x2.toDouble(), y2.toDouble())
    path.lineTo(x3.toDouble(), y3.toDouble())
    path.lineTo(x1.toDouble(), y2.toDouble())
    path.lineTo(x1.toDouble(), (y1 + arc).toDouble())
    path.lineTo(x1 + arc.toDouble(), y1.toDouble())
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
    path.moveTo((x1 + arc).toDouble(), y1.toDouble())
    path.lineTo(x2.toDouble(), y1.toDouble())
    path.lineTo(x3.toDouble(), y2.toDouble())
    path.lineTo(x2.toDouble(), y3.toDouble())
    path.lineTo((x1 + arc).toDouble(), y3.toDouble())
    path.lineTo(x1.toDouble(), (y3 - arc).toDouble())
    // path.quadTo(path.currentPoint.x, path.currentPoint.y, x1.toDouble(), (y3 - arc).toDouble())
    path.lineTo(x1.toDouble(), (y1 + arc).toDouble())
    path.lineTo((x1 + arc).toDouble(), y1.toDouble())
    //path.quadTo(path.currentPoint.x, path.currentPoint.y, (x1 + arc).toDouble(), y1.toDouble())
    path.closePath()
    return path
  }

  override fun calculateThumbLocation() {
    super.calculateThumbLocation()
    if (slider.orientation == JSlider.HORIZONTAL) {
      val valuePosition = xPositionForValue(slider.value)
      thumbRect.x = valuePosition - focusedThumbHalfWidth - 1
      thumbRect.y = trackRect.y
    }
    else {
      val valuePosition = yPositionForValue(slider.value)
      thumbRect.x = trackRect.x
      thumbRect.y = valuePosition - focusedThumbHalfWidth - 1
    }
  }

  override fun setThumbLocation(x: Int, y: Int) {
    if (slider.orientation == JSlider.HORIZONTAL) {
      super.setThumbLocation(x - 1, y)
    }
    else {
      super.setThumbLocation(x, y - 1)
    }

    slider.repaint()
  }

  override fun getBaseline(c: JComponent?, width: Int, height: Int): Int {
    if (slider.orientation == SwingConstants.HORIZONTAL) {
      return thumbOverhang + (2 * focusBorderThickness) + slider.insets.top + borderThickness
    }
    else {
      return super.getBaseline(c, width, height)
    }
  }

  override fun paintTrack(g: Graphics) {
    val g2d = g.create() as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.paint = if (slider.isEnabled) trackColor else disabledTrackColor
    try {
      if (slider.orientation == SwingConstants.HORIZONTAL) {
        val y = thumbRect.y + focusBorderThickness + thumbOverhang - trackThickness
        LinePainter2D.paint(g2d, trackRect.getX(), y.toDouble(), trackRect.maxX, y.toDouble(),
                            LinePainter2D.StrokeType.INSIDE,
                            trackThickness.toDouble())
      }
      else {
        val x = thumbRect.x + focusBorderThickness + thumbOverhang - trackThickness
        LinePainter2D.paint(g2d, x.toDouble(), trackRect.y.toDouble(), x.toDouble(), trackRect.maxY, LinePainter2D.StrokeType.INSIDE,
                            trackThickness.toDouble())
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
    val width = (focusedThumbHalfWidth * 2) + 1
    return if (slider.orientation == SwingConstants.HORIZONTAL) Dimension(width, JBUI.scale(22))
    else Dimension(JBUI.scale(22), width)
  }

  override fun calculateTrackBuffer() {
    if (slider.paintLabels && slider.labelTable != null) {
      val highLabel = highestValueLabel
      val lowLabel = lowestValueLabel
      if (slider.orientation == JSlider.HORIZONTAL) {
        if (highLabel != null && lowLabel != null) {
          trackBuffer = highLabel.bounds.width.coerceAtLeast(lowLabel.bounds.width) / 2
        }
        trackBuffer = trackBuffer.coerceAtLeast(focusedThumbHalfWidth)
      }
      else {
        if (highLabel != null && lowLabel != null) {
          trackBuffer = highLabel.bounds.height.coerceAtLeast(lowLabel.bounds.height) / 2
        }
        trackBuffer = trackBuffer.coerceAtLeast(focusedThumbHalfWidth) + 1
      }
    }
    else {
      trackBuffer = focusedThumbHalfWidth + 1
    }
  }

  override fun calculateLabelRect() {
    super.calculateLabelRect()

    if (slider.orientation == JSlider.HORIZONTAL) {
      labelRect.y += tickBottom
    }
    else {
      labelRect.x += tickBottom
    }
  }

  override fun calculateTickRect() {
    if (slider.orientation == JSlider.HORIZONTAL) {
      tickRect.x = trackRect.x + 1
      tickRect.y = trackRect.y + focusBorderThickness + thumbOverhang + tickTop
      tickRect.width = trackRect.width
      tickRect.height = if (slider.paintTicks) tickLength else 0
    }
    else {
      tickRect.width = if (slider.paintTicks) tickLength else 0
      if (slider.componentOrientation.isLeftToRight) {
        tickRect.x = trackRect.x + focusBorderThickness + thumbOverhang + tickTop
      }
      else {
        tickRect.x = trackRect.x - (focusBorderThickness + thumbOverhang + tickTop)
      }
      tickRect.y = trackRect.y + 1
      tickRect.height = trackRect.height
    }
  }

  override fun getTickLength(): Int {
    return majorTickHeight
  }

  override fun installDefaults(slider: JSlider?) {
    LookAndFeel.installColorsAndFont(slider, "Slider.background",
                                     "Slider.foreground", "Slider.font")
    focusInsets = JBUI.emptyInsets()
  }

  override fun paintMinorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x: Int) {
    g as Graphics2D
    val color = if (slider.isEnabled) tickColor else disabledTickColor
    g.paint2DLine(Point(x, 0), Point(x, minorTickHeight), LinePainter2D.StrokeType.INSIDE, borderThickness.toDouble(), color)
  }

  override fun paintMajorTickForHorizSlider(g: Graphics, tickBounds: Rectangle, x: Int) {
    g as Graphics2D
    val color = if (slider.isEnabled) tickColor else disabledTickColor
    g.paint2DLine(Point(x, 0), Point(x, majorTickHeight), LinePainter2D.StrokeType.INSIDE, borderThickness.toDouble(), color)
  }

  override fun paintMinorTickForVertSlider(g: Graphics, tickBounds: Rectangle, y: Int) {
    g as Graphics2D
    val color = if (slider.isEnabled) tickColor else disabledTickColor
    g.paint2DLine(Point(0, y), Point(minorTickHeight, y), LinePainter2D.StrokeType.INSIDE, borderThickness.toDouble(), color)
  }

  override fun paintMajorTickForVertSlider(g: Graphics, tickBounds: Rectangle, y: Int) {
    g as Graphics2D
    val color = if (slider.isEnabled) tickColor else disabledTickColor
    g.paint2DLine(Point(0, y), Point(majorTickHeight, y), LinePainter2D.StrokeType.INSIDE, borderThickness.toDouble(), color)
  }

  private val thumbHalfWidth = JBUI.scale(7)
  private val focusedThumbHalfWidth = thumbHalfWidth + focusBorderThickness
  private val arc: Int
    get() = JBUI.scale(1)
  private val trackThickness: Int
    get() = JBUI.scale(3)
  private val focusBorderThickness: Int
    get() = JBUI.scale(2)
  private val focusedBorderColor: Color
    get() = JBUI.CurrentTheme.Focus.focusColor()
  private val borderThickness: Int
    get() = JBUI.scale(1)
  private val thumbOverhang: Int
    get() = JBUI.scale(10)
  private val buttonColor: Color
    get() = JBColor.namedColor("Slider.buttonColor", JBColor(0xFFFFFF, 0x9B9E9E))
  private val buttonBorderColor: Color
    get() = JBColor.namedColor("Slider.buttonBorderColor", JBColor(0xA6A6A6, 0x393D3F))
  private val trackColor: Color
    get() = JBColor.namedColor("Slider.trackColor", JBColor(0xC7C7C7, 0x666666))
  private val tickColor: Color
    get() = JBColor.namedColor("Slider.tickColor", JBColor(0x999999, 0x808080))
  private val focusedOuterColor: Color
    get() = JBColor.namedColor("Component.focusedBorderColor", 0x87AFDA)
  private val disabledButtonColor: Color
    get() = JBColor.PanelBackground
  private val disabledButtonBorderColor: Color
    get() = JBColor.namedColor("Component.disabledBorderColor", 0x87AFDA)
  private val disabledTrackColor: Color
    get() = disabledButtonBorderColor
  private val disabledTickColor: Color
    get() = disabledButtonBorderColor
  private val tickTop: Int
    get() = JBUI.scale(6)
  private val tickBottom: Int
    get() = JBUI.scale(2)
  private val minorTickHeight: Int
    get() = JBUI.scale(4)
  private val majorTickHeight: Int
    get() = JBUI.scale(7)
}