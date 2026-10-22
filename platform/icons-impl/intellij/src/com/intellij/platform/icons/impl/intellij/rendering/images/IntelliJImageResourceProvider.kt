// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.UrlImageResourceLocation
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.intelliJThemeContext
import com.intellij.platform.icons.impl.rendering.DefaultImageResourceProvider
import com.intellij.platform.icons.rendering.ThemeContext
import javax.swing.Icon

/**
 * Loads images with the IntelliJ image loaders. The theme inputs come from the theme of the load. A load without a
 * theme takes the theme of the IDE.
 */
class IntelliJImageResourceProvider: DefaultImageResourceProvider() {
  override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource =
    loadImage(location, imageModifiers, intelliJThemeContext)

  override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?, theme: ThemeContext): ImageResource {
    return when (location) {
      is ModuleImageResourceLocation -> ModuleImageResourceLoader().loadImage(location, imageModifiers, theme)
      is UrlImageResourceLocation -> UrlImageResourceLoader().loadImage(location, imageModifiers, theme)
      else -> error("Cannot find loader for location: $location")
    }
  }

  override fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers?): ImageResource =
    fromSwingIcon(icon, imageModifiers, intelliJThemeContext)

  override fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers?, theme: ThemeContext): ImageResource {
    return IntelliJImageResource(LegacyIconImageResourceHolder(icon), imageModifiers, theme)
  }
}
