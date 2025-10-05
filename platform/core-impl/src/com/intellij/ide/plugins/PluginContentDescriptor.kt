// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class PluginContentDescriptor(@JvmField val modules: List<ModuleItem>) {
  companion object {
    @JvmField val EMPTY: PluginContentDescriptor = PluginContentDescriptor(Java11Shim.INSTANCE.listOf())
  }

  @ApiStatus.Internal
  class ModuleItem(
    val moduleId: PluginModuleId,
    val configFile: String?,
    internal val descriptorContent: CharArray?,
    val loadingRule: ModuleLoadingRule,
  ) {
    /**
     * all content module descriptors are assigned during plugin descriptor loading
     */
    private var _descriptor: ContentModuleDescriptor? = null

    fun assignDescriptor(descriptor: ContentModuleDescriptor) {
      _descriptor = descriptor
    }

    fun requireDescriptor(): ContentModuleDescriptor = _descriptor ?: throw IllegalStateException("Descriptor is not set for $this")

    /**
     * after the plugin is loaded, all descriptors are set
     */
    val descriptor: ContentModuleDescriptor
      get() = requireDescriptor()

    @TestOnly
    fun getDescriptorOrNull(): ContentModuleDescriptor? = _descriptor

    override fun toString(): String = "ModuleItem(id=$moduleId, descriptor=$_descriptor, configFile=$configFile)"
  }

  override fun toString(): String = "PluginContentDescriptor(modules=$modules)"
}