// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginModuleId.Companion.getId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.SystemProperties
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an identifier of a plugin content module, consisting of a module name and namespace.
 *
 * This class is not supposed to be used in API.
 */
@ApiStatus.Internal
@IntellijInternalApi
class PluginModuleId private constructor(val name: String, val namespace: String) {
  override fun toString(): String = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginModuleId

    return name == other.name && (!useNamespaceInId || namespace == other.namespace)
  }

  override fun hashCode(): Int {
    return if (useNamespaceInId) name.hashCode() + 31 * namespace.hashCode() else name.hashCode()
  }

  companion object {
    private val interner = CollectionFactory.createConcurrentWeakKeyWeakValueMap<String, PluginModuleId>()
    /** this property is temporarily added to allow using modules without specifying namespace */
    private val useNamespaceInId = SystemProperties.getBooleanProperty("intellij.platform.plugin.modules.use.namespace.in.id", false)

    @JvmStatic
    fun getId(name: String, namespace: String): PluginModuleId {
      val interned = interner[name]
      /* Strictly speaking, a key composed of 'name' and 'namespace' should be used. However, in almost all cases names will be unique, so using composite keys won't bring value
         but may affect performance. Also, we'll need to store concatenated values somewhere in the model to ensure that GC won't collect the corresponding entries. */
      if (interned != null && interned.namespace == namespace) {
        return interned
      }
      val moduleId = PluginModuleId(name, namespace)
      val old = interner.putIfAbsent(name, moduleId)
      if (old != null && old.namespace == namespace) {
        return old
      }
      return moduleId
    }

    /** shorthand for [getId] in kotlin */
    operator fun invoke(name: String, namespace: String): PluginModuleId = getId(name, namespace)

    /**
     * The namespace used for modules from the IntelliJ Platform and plugins developed by JetBrains.
     * It's used by default when declaring a dependency if the namespace isn't specified explicitly.
     */
    const val JETBRAINS_NAMESPACE: String = "jetbrains"
  }
}