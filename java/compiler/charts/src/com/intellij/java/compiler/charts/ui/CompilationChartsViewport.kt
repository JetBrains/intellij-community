// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.ui.components.JBViewport
import com.intellij.ui.components.Magnificator
import com.intellij.ui.components.ZoomingDelegate
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent


class CompilationChartsViewport(private val zoom: Zoom) : JBViewport(), Magnificator {
  override fun getMagnificator(): Magnificator = this

  override fun createZooming(): ZoomingDelegate = CompilationChartsZoomingDelegate(view as JComponent, this, zoom)

  override fun magnify(magnification: Double, at: Point): Point {
    zoom.adjustUser(this, at.x, scale(magnification))
    revalidate()
    repaint()
    return at
  }

  private class CompilationChartsZoomingDelegate(private val component: JComponent,
                                                 private val viewport: JBViewport,
                                                 private val zoom: Zoom) : ZoomingDelegate(component, viewport) {

    private var cachedImage: BufferedImage? = null
    private var magnificationPoint: Point? = null
    private var magnification = 0.0

    override fun paint(g: Graphics) {
      if (g !is Graphics2D) return
      val image = cachedImage ?: return
      val point = magnificationPoint ?: return

      val scale = scale(magnification)
      val xOffset = (point.x - point.x * scale).toInt()
      val yOffset = 0

      val clip: Rectangle = g.clipBounds
      g.color = component.getBackground()
      g.fillRect(clip.x, clip.y, clip.width, clip.height)

      val translated = g.create() as Graphics2D
      translated.translate(xOffset, yOffset)
      translated.scale(scale, 1.0)
      UIUtil.drawImage(translated, image, 0, 0, null)
      translated.dispose()
    }

    override fun magnificationStarted(at: Point) {
      magnificationPoint = at
      cacheImage()
    }

    override fun magnificationFinished(magnification: Double) {
      magnificationPoint?.run {
        zoom.adjustUser(viewport, x, scale(magnification))
      }
      clearCache()
    }

    override fun magnify(magnification: Double) {
      this.magnification = magnification
      viewport.repaint()
    }

    private fun cacheImage() {
      val bounds: Rectangle = viewport.bounds
      if (bounds.width <= 0 || bounds.height <= 0) return

      val image: BufferedImage = ImageUtil.createImage(viewport.graphics, bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB)
      val graphics = image.graphics
      graphics.setClip(0, 0, bounds.width, bounds.height)
      viewport.paint(graphics)
      cachedImage = image
    }

    private fun clearCache() {
      cachedImage = null
      magnificationPoint = null
      magnification = 0.0
    }

    override fun isActive(): Boolean = cachedImage != null
  }

  companion object {
    fun scale(magnification: Double): Double = if (magnification < 0) 1f / (1 - magnification) else (1 + magnification)
    // exp(scale * -0.05)
  }
}