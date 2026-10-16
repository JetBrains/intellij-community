// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginInitializationContext.EnvironmentConfiguredModuleData
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.productMode.ProductMode
import com.intellij.platform.productMode.ProductModeEnvironmentModules
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * The plugin model rules that a [ProductMode] implies.
 *
 * These rules are product policy, so they stay here and not on [ProductMode]. [ProductMode] carries
 * only the structural facts of a mode. The availability of the environment-configured modules is in
 * [ProductModeEnvironmentModules], because the build-time plugin collector reads it too.
 */
@ApiStatus.Internal
object ProductModeCapabilities {
  /**
   * `true` when the mode needs the remote development plugin.
   * A monolith, a standalone light process and a language server run without it.
   */
  internal val ProductMode.requiresRemoteDevPlugin: Boolean
    get() = isFrontendProcess || this == ProductMode.BACKEND

  @ApiStatus.Internal
  @VisibleForTesting
  fun computeEssentialPlugins(
    declaredEssentialPlugins: List<PluginId>,
    productMode: ProductMode,
  ): Set<PluginId> = buildSet {
    add(CORE_ID)
    addAll(declaredEssentialPlugins)
    if (productMode.requiresRemoteDevPlugin) {
      add(REMOTE_DEVELOPMENT_PLUGIN_ID)
    }
  }

  @ApiStatus.Internal
  @VisibleForTesting
  fun MutableMap<PluginModuleId, EnvironmentConfiguredModuleData>.configureProductModeModules(productMode: ProductMode) {
    fun setModuleAvailability(moduleId: PluginModuleId, isAvailable: Boolean) {
      val moduleData =
        if (isAvailable) EnvironmentConfiguredModuleData(null)
        else EnvironmentConfiguredModuleData(UnsuitableProductModeModuleUnavailabilityReason(moduleId, productMode.id))
      val replaced = this.put(moduleId, moduleData)
      check(replaced == null) { "${moduleId.displayName} is already registered as environment-configured module" }
    }

    for ((moduleName, isAvailable) in ProductModeEnvironmentModules.availability(productMode)) {
      setModuleAvailability(PluginModuleId(moduleName, PluginModuleId.JETBRAINS_NAMESPACE), isAvailable)
    }
  }

  private val REMOTE_DEVELOPMENT_PLUGIN_ID: PluginId = PluginId.getId("com.jetbrains.remoteDevelopment")
}
