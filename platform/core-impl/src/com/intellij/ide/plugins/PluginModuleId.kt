// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginModuleId.Companion.getId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus

/**
 * In plugin configuration files modules are referred to by `name`s, but here we intentionally use `id` instead.
 *
 * This class is not supposed to be used in API.
 */
@ApiStatus.Internal
@IntellijInternalApi
class PluginModuleId private constructor(val id: String) {
  override fun toString(): String = id

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginModuleId

    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    private val interner = CollectionFactory.createConcurrentWeakKeyWeakValueMap<String, PluginModuleId>()

    fun getId(id: String): PluginModuleId {
      val interned = interner[id]
      if (interned != null) {
        return interned
      }
      val moduleId = PluginModuleId(id)
      val old = interner.putIfAbsent(id, moduleId)
      return old ?: moduleId
    }

    /** shorthand for [getId] in kotlin */
    operator fun invoke(id: String): PluginModuleId = getId(id)
  }
}