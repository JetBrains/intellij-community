// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.ReviseWhenPortedToJDK
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained
 * via [com.intellij.ide.plugins.PluginManagerCore.getPlugin] method.
 */
@Serializable
class PluginId private constructor(val idString: String) : Comparable<PluginId> {

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is PluginId) return false

    val pluginId = o
    return idString == pluginId.idString
  }

  override fun hashCode(): Int {
    return idString.hashCode()
  }

  override fun compareTo(o: PluginId): Int {
    return idString.compareTo(o.idString)
  }

  override fun toString(): String {
    return idString
  }

  companion object {
    @ApiStatus.Internal
    val EMPTY_ARRAY: Array<PluginId> = emptyArray()

    private val registeredIds: MutableMap<String, PluginId> = ConcurrentHashMap<String, PluginId>()

    @JvmStatic
    fun getId(idString: String): PluginId {
      return registeredIds.computeIfAbsent(idString) { idString: String -> PluginId(idString) }
    }

    @JvmStatic
    fun findId(idString: String?): PluginId? {
      return registeredIds[idString]
    }

    @JvmStatic
    fun findId(vararg idStrings: String): PluginId? {
      for (idString in idStrings) {
        val pluginId: PluginId? = registeredIds[idString]
        if (pluginId != null) {
          return pluginId
        }
      }
      return null
    }

    @JvmStatic
    @ApiStatus.Internal
    @ReviseWhenPortedToJDK(value = "10", description = "Collectors.toUnmodifiableSet()")
    fun getRegisteredIds(): @Unmodifiable Set<PluginId> {
      return registeredIds.values.toSet()
    }
  }
}
