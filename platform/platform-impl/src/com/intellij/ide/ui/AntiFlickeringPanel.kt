// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ui.DirtyUI
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent

/** A hacky way to reduce flickering. */
@ApiStatus.Internal
class AntiFlickeringPanel(private val content: JComponent) : JBLayeredPane() {
  private var savedSelfieImage: BufferedImage? = null

  inner class FreezingPaintPanel : JComponent() {
    override fun paintComponents(g: Graphics) {
      super.paintComponents(g)
    }

    override fun getSize(): Dimension {
      if (true) return super.size
      return Dimension(200, 200)
    }

    @DirtyUI
    override fun paint(g: Graphics) {
      val image = savedSelfieImage
      if (image != null) {
        UIUtil.drawImage(g, image, 0, 0, null)
      }
    }
  }

  private val freezingPaintPanel = FreezingPaintPanel()

  init {
    isFullOverlayLayout = true
    add(content, DEFAULT_LAYER, 0)
  }

  override fun addImpl(comp: Component, constraints: Any?, index: Int) {
    require(comp == content || comp == freezingPaintPanel) {
      "Alien component: $comp"
    }
    super.addImpl(comp, constraints, index)
  }

  fun freezePainting(delay: Int) {
    if (savedSelfieImage != null) {
      return
    }
    savedSelfieImage = takeSelfie(content)
    if (savedSelfieImage == null) {
      return
    }

    add(freezingPaintPanel, PALETTE_LAYER, 1)

    EdtExecutorService.getScheduledExecutorInstance().schedule(
      {
        remove(freezingPaintPanel)
        savedSelfieImage = null
        revalidate()
        repaint()
      },
      delay.toLong(),
      java.util.concurrent.TimeUnit.MILLISECONDS
    )
  }

  companion object {
    @JvmStatic
    private fun takeSelfie(component: Component): BufferedImage? {
      val graphicsConfiguration = component.graphicsConfiguration ?: return null
      val image = ImageUtil.createImage(graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
      setupAntialiasing(image.graphics)
      component.paint(image.graphics)
      return image
    }
  }
}
