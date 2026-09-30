// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceLoader
import com.intellij.ui.icons.createImageDataLoaderByUrl

class UrlImageResourceLoader: ImageResourceLoader<UrlImageResourceLocation> {
  override fun loadImage(
    location: UrlImageResourceLocation,
    imageModifiers: ImageModifiers?,
  ): ImageResource {
    val dataLoader = createImageDataLoaderByUrl(location.toUrl())
    return IntelliJImageResource(DataLoaderImageResourceHolder(dataLoader), imageModifiers)
  }
}
