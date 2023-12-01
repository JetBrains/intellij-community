// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ui.DirtyUI
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.LayoutManager
import java.awt.image.BufferedImage
import javax.swing.JPanel

/** A hacky way to reduce flickering. */
@ApiStatus.Internal
class AntiFlickeringPanel(layout: LayoutManager?) : JPanel(layout) {
  private var savedSelfieImage: BufferedImage? = null
  private var savedSize: Dimension? = null
  private var savedPreferredSize: Dimension? = null

  fun freezePainting(delay: Int) {
    isOpaque = true
    savedSelfieImage = takeSelfie(this)
    savedSize = size
    savedPreferredSize = preferredSize

    val alarm = SingleAlarm({
                              savedSelfieImage = null
                              savedSize = null
                              savedPreferredSize = null
                              isOpaque = false
                              revalidate()
                              repaint()
                            }, delay, null)
    alarm.request()
  }

  override fun getSize(): Dimension {
    return savedSize ?: super.getSize()
  }

  override fun getPreferredSize(): Dimension {
    return savedPreferredSize ?: super.getPreferredSize()
  }

  @DirtyUI
  override fun paint(g: Graphics) {
    val image = savedSelfieImage
    if (image != null) {
      System.err.println("image")
      UIUtil.drawImage(g, image, 0, 0, null)
      return
    }
    super.paint(g)
  }

  companion object {
    @JvmStatic
    private fun takeSelfie(component: Component): BufferedImage {
      val graphicsConfiguration = component.graphicsConfiguration
      val image = ImageUtil.createImage(graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
      setupAntialiasing(image.graphics)
      component.paint(image.graphics)
      return image
    }
  }
}
