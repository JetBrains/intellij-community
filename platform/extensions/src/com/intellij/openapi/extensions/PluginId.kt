// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.extensions.PluginId.Companion.getId
import com.intellij.util.containers.Interner
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained
 * via [com.intellij.ide.plugins.PluginManagerCore.getPlugin] method.
 */
@Serializable
class PluginId private constructor(val idString: String) : Comparable<PluginId> {

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is PluginId) return false

    return idString == o.idString
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
    private val interner = Interner.createWeakInterner<PluginId>()

    /**
     * Shorthand for [getId]
     */
    operator fun invoke(idString: String): PluginId = getId(idString)

    @JvmStatic
    fun getId(idString: String): PluginId = interner.intern(PluginId(idString))

    @Deprecated("Use getId", ReplaceWith("getId(idString)"))
    @ApiStatus.ScheduledForRemoval
    @JvmStatic
    fun findId(idString: String?): PluginId? = idString?.let(::getId)

    @Deprecated("Use getId", ReplaceWith("getId(idStrings[0])"))
    @ApiStatus.ScheduledForRemoval
    @JvmStatic
    fun findId(vararg idStrings: String): PluginId? = idStrings.firstOrNull()?.let(::getId)
  }
}
