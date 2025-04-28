// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class PluginContentDescriptor(@JvmField val modules: List<ModuleItem>) {
  companion object {
    @JvmField val EMPTY: PluginContentDescriptor = PluginContentDescriptor(Java11Shim.INSTANCE.listOf())
  }

  @ApiStatus.Internal
  class ModuleItem(
    override val name: String,
    val configFile: String?,
    internal val descriptorContent: CharArray?,
    override val loadingRule: ModuleLoadingRule,
  ): ContentModule {
    /**
     * all content module descriptors are assigned during plugin descriptor loading
     */
    private var _descriptor: IdeaPluginDescriptorImpl? = null

    fun assignDescriptor(descriptor: IdeaPluginDescriptorImpl) {
      _descriptor = descriptor
    }

    fun requireDescriptor(): IdeaPluginDescriptorImpl = _descriptor ?: throw IllegalStateException("Descriptor is not set for $this")

    /**
     * after the plugin is loaded, all descriptors are set
     */
    override val descriptor: IdeaPluginDescriptorEx
      get() = requireDescriptor()

    @TestOnly
    fun getDescriptorOrNull(): IdeaPluginDescriptorImpl? = _descriptor

    override fun toString(): String = "ModuleItem(name=$name, descriptor=$_descriptor, configFile=$configFile)"
  }

  override fun toString(): String = "PluginContentDescriptor(modules=$modules)"
}