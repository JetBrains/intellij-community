// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.getMainDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceLoader
import com.intellij.ui.icons.findIconLoaderByPath

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

  private fun getClassLoader(pluginId: String, moduleId: String?): ClassLoader? {
    val plugin = PluginManagerCore.findPlugin(PluginId.getId(pluginId)) ?: return null
    if (moduleId == null) {
      return plugin.pluginClassLoader
    }
    else {
      return plugin.getMainDescriptor().contentModules.firstOrNull { it.moduleId.name == moduleId }?.pluginClassLoader
    }
  }
}