// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.util.containers.Interner
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained via `PluginManagerCore.getPlugin`.
 */
@Serializable
class PluginId private constructor(val idString: String) : Comparable<PluginId> {
  override fun equals(other: Any?): Boolean = this === other || other is PluginId && idString == other.idString

  override fun hashCode(): Int = idString.hashCode()

  override fun compareTo(other: PluginId): Int = idString.compareTo(other.idString)

  override fun toString(): String = idString

  companion object {
    private val interner = Interner.createWeakInterner<PluginId>()

    /** Shorthand for [getId] */
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
