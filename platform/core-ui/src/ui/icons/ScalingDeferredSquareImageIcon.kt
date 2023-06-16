// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.IconDeferrer
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextCache
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon

class ScalingDeferredSquareImageIcon<K : Any>(size: Int, defaultIcon: Icon, private val key: K, imageLoader: (K) -> Image?) : Icon {
  private val baseIcon = IconUtil.resizeSquared(defaultIcon, size)

  private val scaledIconCache = ScaleContextCache { scaleContext ->
    IconDeferrer.getInstance().defer(baseIcon, key) {
      try {
        imageLoader(it)?.let { image ->
          val resizedImage = ImageUtil.resize(image, size, scaleContext)
          IconUtil.createImageIcon(resizedImage)
        } ?: baseIcon
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