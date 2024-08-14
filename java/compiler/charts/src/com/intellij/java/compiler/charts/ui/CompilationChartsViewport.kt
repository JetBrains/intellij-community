// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.ui.components.JBViewport
import com.intellij.ui.components.ZoomingDelegate
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

class CompilationChartsViewport(private val zoom: Zoom) : JBViewport() {
  override fun createZooming(): ZoomingDelegate = CompilationChartsZoomingDelegate(view as JComponent, this, zoom)

  private class CompilationChartsZoomingDelegate(private val component: JComponent, private val viewport: JBViewport, private val zoom: Zoom) : ZoomingDelegate(component, viewport) {
    private var magnificationPoint: Point? = null
    private var magnification = 0.0

    override fun paint(g: Graphics) {
      if (g !is Graphics2D) return
      val point = magnificationPoint ?: return

      val scale: Double = scale(magnification)
      val xOffset = (point.x - point.x * scale).toInt()
      val yOffset = 0

      val clip: Rectangle = g.clipBounds
      g.color = component.getBackground()
      g.fillRect(clip.x, clip.y, clip.width, clip.height)

      val translated = g.create() as Graphics2D
      translated.translate(xOffset, yOffset)
      translated.scale(scale, 1.0)
      component.paint(translated)
      translated.dispose()
    }

    override fun magnificationStarted(at: Point) {
      magnificationPoint = at
    }

    override fun magnificationFinished(magnification: Double) {
      magnificationPoint?.run {
        zoom.adjustUser(viewport, x, scale(magnification))
      }
      magnificationPoint = null
      this.magnification = 0.0
    }

    override fun magnify(magnification: Double) {
      this.magnification = magnification
      viewport.repaint()
    }

    override fun isActive(): Boolean = magnificationPoint != null
  }

  companion object {
    fun scale(magnification: Double): Double = if (magnification < 0) 1f / (1 - magnification) else (1 + magnification)
  }
}