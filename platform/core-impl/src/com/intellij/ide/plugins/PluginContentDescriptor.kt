// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
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
    private val loadingRule: ModuleLoadingRule,
    private val requiredIfAvailable: PluginModuleId?,
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

    /**
     * Note: the effective loading rule depends on the app environment context (e.g., frontend/backend/monolith mode)
     * @see determineLoadingRule
     */
    val defaultLoadingRule: ModuleLoadingRule get() = loadingRule

    // TODO this logic should happen in plugin pre-init stage, not after descriptor parsing
    fun determineLoadingRule(initContext: PluginInitializationContext, diagnosticPluginId: PluginId): ModuleLoadingRule {
      if (requiredIfAvailable == null) {
        return loadingRule
      }
      if (loadingRule == ModuleLoadingRule.EMBEDDED || loadingRule == ModuleLoadingRule.REQUIRED) {
        return loadingRule
      }
      val targetModule = initContext.environmentConfiguredModules[requiredIfAvailable]
      if (targetModule == null) {
        // TODO should lift this log out of here
        logger<PluginManagerCore>().error("Plugin id='$diagnosticPluginId' uses required-if-available statement in content module '${moduleId.name}' " +
                                          "with a target module that is unknown or is not configured by the environment: $requiredIfAvailable")
        return loadingRule
      }
      if (targetModule.isAvailable) {
        return ModuleLoadingRule.REQUIRED
      }
      return loadingRule
    }

    override fun toString(): String = "ModuleItem(id=$moduleId, descriptor=$_descriptor, configFile=$configFile)"
  }

  override fun toString(): String = "PluginContentDescriptor(modules=$modules)"
}