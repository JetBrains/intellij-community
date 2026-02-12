// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import kotlinx.serialization.Serializable
import org.jetbrains.icons.ImageResourceLocation

@Serializable
class ModuleImageResourceLocation(
  @JvmField val path: String,
  @JvmField val pluginId: String,
  @JvmField val moduleId: String?,
): ImageResourceLocation {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ModuleImageResourceLocation

    if (path != other.path) return false
    if (pluginId != other.pluginId) return false
    if (moduleId != other.moduleId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + pluginId.hashCode()
    result = 31 * result + (moduleId?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "ModuleImageResourceLoader(path='$path', pluginId='$pluginId', moduleId=$moduleId)"
  }

  companion object {
    fun fromClassLoader(path: String, classLoader: ClassLoader): ImageResourceLocation {
      val (pluginId, moduleId) = getPluginAndModuleId(classLoader)
      return ModuleImageResourceLocation(path, pluginId, moduleId)
    }

    private fun getPluginAndModuleId(classLoader: ClassLoader): Pair<String, String?> {
      if (classLoader is PluginAwareClassLoader) {
        return classLoader.pluginId.idString to classLoader.moduleId
      }
      else {
        return "com.intellij" to null
      }
    }
  }

}
