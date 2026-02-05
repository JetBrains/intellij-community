// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import java.awt.Image

internal class DataLoaderImageResourceHolder(
  val dataLoader: ImageDataLoader
) : SwingImageResourceHolder {
  override fun getImage(
    scale: ScaleContext,
    imageModifiers: ImageModifiers?,
  ): Image? {
    return dataLoader.loadImage(
      parameters = imageModifiers.toLoadParameters(),
      scaleContext = scale
    )
  }

  override fun getExpectedDimensions(): Dimensions {
    val img = getImage(ScaleContext.create(), null) ?: return Dimensions(0, 0)
    return Dimensions(img.getWidth(null), img.getHeight(null))
  }
}
