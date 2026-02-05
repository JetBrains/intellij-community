// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.rendering.DefaultImageResourceProvider
import javax.swing.Icon

class IntelliJImageResourceProvider: DefaultImageResourceProvider() {
  override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
    val loader = if (location is ModuleImageResourceLocation) {
      ModuleImageResourceLoader()
    } else null
    if (loader == null) error("Cannot find loader for location: $location")
    return loader.loadGenericImage(location, imageModifiers) ?: error("Cannot load image for location: $location")
  }

  override fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers?): ImageResource {
    return IntelliJImageResource(LegacyIconImageResourceHolder(icon), imageModifiers)
  }
}