// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.patchers.SvgPatcher

@ApiStatus.Experimental
fun imageResource(loader: ImageResourceLocation, imageModifiers: ImageModifiers? = null): ImageResource = ImageResourceProvider.getInstance().loadImage(loader, imageModifiers)

@ApiStatus.Experimental
interface ImageModifiers {
  val colorFilter: ColorFilter?
  val svgPatcher: SvgPatcher?
}

@ApiStatus.Experimental
interface ImageResource {
  /**
   * Image width in pixels, if the image is rescalable this should return default size or null if default size is not set.
   */
  val width: Int?
  /**
   * Image height in pixels, if the image is rescalable this should return default size or null if default size is not set.
   */
  val height: Int?

  companion object
}