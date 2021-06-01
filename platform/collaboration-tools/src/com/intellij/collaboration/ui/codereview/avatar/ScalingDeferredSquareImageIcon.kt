// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.avatar

import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon

class ScalingDeferredSquareImageIcon<K : Any>(size: Int, defaultIcon: Icon,
                                              private val key: K,
                                              imageLoader: (K) -> Image?) : Icon {
  private val baseIcon = IconUtil.resizeSquared(defaultIcon, size)

  private val scaledIconCache = ScaleContext.Cache<Icon> { scaleCtx ->
    DeferredIconImpl(baseIcon, key, false) {
      try {
        val image = imageLoader(it)
        val hidpiImage = ImageUtil.ensureHiDPI(image, scaleCtx)
        val scaledSize = scaleCtx.apply(size.toDouble(), ScaleType.USR_SCALE).toInt()
        val scaledImage = ImageUtil.scaleImage(hidpiImage, scaledSize, scaledSize)
        IconUtil.createImageIcon(scaledImage)
      }
      catch (e: Exception) {
        baseIcon
      }
    }
  }

  override fun getIconHeight() = baseIcon.iconHeight
  override fun getIconWidth() = baseIcon.iconWidth

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    scaledIconCache.getOrProvide(ScaleContext.create(c))?.paintIcon(c, g, x, y)
  }
}