// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginModuleId.Companion.getId
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.containers.Interner
import org.jetbrains.annotations.ApiStatus

/**
 * In plugin configuration files modules are referred to by `name`s, but here we intentionally use `id` instead.
 *
 * This class is not supposed to be used in API.
 */
@JvmInline
@ApiStatus.Internal
@IntellijInternalApi
value class PluginModuleId private constructor(val id: String){
  override fun toString(): String = id

  companion object {
    // PluginModuleId can be either boxed or unboxed, so only interning of value matters
    private val interner = Interner.createWeakInterner<String>()

    fun getId(id: String): PluginModuleId = PluginModuleId(interner.intern(id))

    /** shorthand for [getId] in kotlin */
    operator fun invoke(id: String): PluginModuleId = getId(id)

    @Deprecated("plugin and module id namespaces are separate")
    fun PluginId.asPluginModuleId(): PluginModuleId = getId(idString)

    @Deprecated("plugin and module id namespaces are separate")
    fun PluginModuleId.asPluginId(): PluginId = PluginId.getId(id)
  }
}