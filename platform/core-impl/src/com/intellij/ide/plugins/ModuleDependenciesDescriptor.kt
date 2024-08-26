// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Java11Shim
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class ModuleDependenciesDescriptor(@JvmField val modules: List<ModuleReference>, @JvmField val plugins: List<PluginReference>) {
  companion object {
    @JvmField val EMPTY: ModuleDependenciesDescriptor = ModuleDependenciesDescriptor(Collections.emptyList(), Collections.emptyList())
  }

  class ModuleReference(@JvmField val name: String) {
    override fun toString(): String = "ModuleItem(name=$name)"
  }

  class PluginReference(@JvmField val id: PluginId) {
    override fun toString(): String = "PluginReference(id=$id)"
  }

  override fun toString(): String = "ModuleDependenciesDescriptor(modules=$modules, plugins=$plugins)"
}

@ApiStatus.Internal
class PluginContentDescriptor(@JvmField val modules: List<ModuleItem>) {
  companion object {
    @JvmField val EMPTY: PluginContentDescriptor = PluginContentDescriptor(Java11Shim.INSTANCE.listOf())
  }

  @ApiStatus.Internal
  class ModuleItem(
    @JvmField val name: String,
    @JvmField val configFile: String?,
    @JvmField internal val descriptorContent: CharArray?,
    @JvmField val loadingRule: ModuleLoadingRule,
  ) {
    @JvmField
    internal var descriptor: IdeaPluginDescriptorImpl? = null

    fun requireDescriptor(): IdeaPluginDescriptorImpl = descriptor ?: throw IllegalStateException("Descriptor is not set for $this")

    override fun toString(): String = "ModuleItem(name=$name, descriptor=$descriptor, configFile=$configFile)"
  }

  override fun toString(): String = "PluginContentDescriptor(modules=$modules)"
}