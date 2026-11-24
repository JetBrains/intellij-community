// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.getMainDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.intelliJThemeContext
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceLoader
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.icons.findReflectivePatchTarget
import org.jetbrains.annotations.ApiStatus

class ModuleImageResourceLoader: ImageResourceLoader<ModuleImageResourceLocation> {
  override fun loadImage(
      location: ModuleImageResourceLocation,
      imageModifiers: ImageModifiers?,
  ): ImageResource = loadImage(location, imageModifiers, intelliJThemeContext)

  /** Loads the image with the theme inputs of [theme], read on each rasterization. */
  fun loadImage(
      location: ModuleImageResourceLocation,
      imageModifiers: ImageModifiers?,
      theme: ThemeContext,
  ): ImageResource {
    val classLoader = location.findClassLoader()
                      ?: error("Cannot recover classloader for plugin: ${location.pluginId} module: ${location.moduleId}")
    // like `findIconByPath`, a theme patch to a reflective icon draws that icon, with its own modifications
    findReflectivePatchTarget(location.path, classLoader)?.let {
      return IntelliJImageResource(LegacyIconImageResourceHolder(it), imageModifiers, theme)
    }
    val dataLoader = findIconLoaderByPath(location.path, classLoader)
    return IntelliJImageResource(DataLoaderImageResourceHolder(dataLoader), imageModifiers, theme)
  }
}

/**
 * The class loader the image of this location is loaded with: the loader of its content module, or of its plugin when
 * it names no module. Null when that plugin or module is not loaded.
 */
@ApiStatus.Internal
fun ModuleImageResourceLocation.findClassLoader(): ClassLoader? {
  val plugin = PluginManagerCore.findPlugin(PluginId.getId(pluginId)) ?: return null
  if (moduleId == null) {
    return plugin.pluginClassLoader
  }
  else {
    return plugin.getMainDescriptor().contentModules.firstOrNull { it.moduleId.name == moduleId }?.pluginClassLoader
  }
}