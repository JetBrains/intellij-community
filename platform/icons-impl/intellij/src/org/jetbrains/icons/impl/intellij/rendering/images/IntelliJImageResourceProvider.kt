// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering.images

import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.impl.intellij.ModuleImageResourceLocation
import org.jetbrains.icons.impl.intellij.rendering.ImageResourceLoaderExtension
import org.jetbrains.icons.impl.rendering.DefaultImageResourceProvider
import javax.swing.Icon

class IntelliJImageResourceProvider: DefaultImageResourceProvider() {
  override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
    val loader = if (location is ModuleImageResourceLocation) {
      ModuleImageResourceLoader()
    } else {
      ImageResourceLoaderExtension.getLoaderFor(location)
    }
    if (loader == null) error("Cannot find loader for location: $location")
    return loader.loadGenericImage(location, imageModifiers) ?: error("Cannot load image for location: $location")
  }

  override fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers?): ImageResource {
    return IntelliJImageResource(LegacyIconImageResourceHolder(icon), imageModifiers)
  }
}