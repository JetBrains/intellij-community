// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.FileImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.intelliJThemeContext
import com.intellij.platform.icons.rendering.EmptyBitmapImageResource
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceLoader
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.ui.icons.createImageDataLoaderByUrl

/** Loads the image file of a [FileImageResourceLocation], which resolves its path only here. */
class FileImageResourceLoader : ImageResourceLoader<FileImageResourceLocation> {
  override fun loadImage(
    location: FileImageResourceLocation,
    imageModifiers: ImageModifiers?,
  ): ImageResource = loadImage(location, imageModifiers, intelliJThemeContext)

  /** Loads the image with the theme inputs of [theme], read on each rasterization. */
  fun loadImage(
    location: FileImageResourceLocation,
    imageModifiers: ImageModifiers?,
    theme: ThemeContext,
  ): ImageResource {
    // a path on a network share has no URL: draw nothing
    val url = location.toUrl() ?: return EmptyBitmapImageResource
    val dataLoader = createImageDataLoaderByUrl(url)
    return IntelliJImageResource(DataLoaderImageResourceHolder(dataLoader), imageModifiers, theme)
  }
}
