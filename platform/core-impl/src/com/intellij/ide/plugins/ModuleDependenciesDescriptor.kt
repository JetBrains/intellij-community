// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.util.*

class ModuleDependenciesDescriptor(@JvmField val modules: List<ModuleReference>, @JvmField val plugins: List<PluginReference>) {
  companion object {
    @JvmField val EMPTY = ModuleDependenciesDescriptor(Collections.emptyList(), Collections.emptyList())
  }

  class ModuleReference(@JvmField val name: String) {
    override fun toString() = "ModuleItem(name=$name)"
  }

  class PluginReference(@JvmField val id: PluginId) {
    override fun toString() = "PluginReference(id=$id)"
  }

  override fun toString() = "ModuleDependenciesDescriptor(modules=$modules, plugins=$plugins)"
}

@ApiStatus.Internal
class PluginContentDescriptor(@JvmField val modules: List<ModuleItem>) {
  companion object {
    @JvmField val EMPTY = PluginContentDescriptor(Collections.emptyList())
  }

  @ApiStatus.Internal
  class ModuleItem(@JvmField val name: String,
                   @JvmField val configFile: String?) {
    @JvmField internal var descriptor: IdeaPluginDescriptorImpl? = null

    fun requireDescriptor() = descriptor ?: throw IllegalStateException("Descriptor is not set for $this")

    override fun toString() = "ModuleItem(name=$name, descriptor=$descriptor, configFile=$configFile)"
  }

  override fun toString(): String {
    return "PluginContentDescriptor(modules=$modules)"
  }
}