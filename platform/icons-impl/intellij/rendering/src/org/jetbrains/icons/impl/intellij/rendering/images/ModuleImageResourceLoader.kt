// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering.images

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.contentModules
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.ui.icons.findIconLoaderByPath
import org.jetbrains.icons.impl.intellij.ModuleImageResourceLocation
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceLoader

class ModuleImageResourceLoader: ImageResourceLoader<ModuleImageResourceLocation> {
  override fun loadImage(
      location: ModuleImageResourceLocation,
      imageModifiers: ImageModifiers?,
  ): ImageResource {
    val classLoader = getClassLoader(location.pluginId, location.moduleId)
                      ?: error("Cannot recover classloader for plugin: ${location.pluginId} module: ${location.moduleId}")
    val dataLoader = findIconLoaderByPath(location.path, classLoader)
    return IntelliJImageResource(DataLoaderImageResourceHolder(dataLoader), imageModifiers)
  }

  @OptIn(IntellijInternalApi::class)
  private fun getClassLoader(pluginId: String, moduleId: String?): ClassLoader? {
    val plugin = PluginManagerCore.findPlugin(PluginId.Companion.getId(pluginId)) ?: return null
    if (moduleId == null) {
      return plugin.classLoader
    }
    else {
      return plugin.contentModules.firstOrNull { it.moduleId.name == moduleId }?.classLoader
    }
  }
}