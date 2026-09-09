// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginInitializationContext.EnvironmentConfiguredModuleData
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * The plugin model rules that a [ProductMode] implies.
 */
@ApiStatus.Internal
object ProductModeCapabilities {
  internal val ProductMode.providesBackendModule: Boolean
    get() = this == ProductMode.MONOLITH || this == ProductMode.BACKEND || this == ProductMode.LANGUAGE_SERVER

  internal val ProductMode.providesFrontendModule: Boolean
    get() = this == ProductMode.MONOLITH || this == ProductMode.FRONTEND ||
            this == ProductMode.LIGHT || this == ProductMode.LIGHT_WITH_RD_CONNECTION // TODO: Subject to change when product mode for monolith light is ready

  /** `true` when the mode needs the remote development plugin. A monolith and a language server run without it. */
  internal val ProductMode.requiresRemoteDevPlugin: Boolean
    get() = this == ProductMode.FRONTEND || this == ProductMode.BACKEND ||
            this == ProductMode.LIGHT || this == ProductMode.LIGHT_WITH_RD_CONNECTION // TODO: Subject to change when product mode for monolith light is ready

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

    setModuleAvailability(FRONTEND_MODULE_ID, productMode.providesFrontendModule)
    setModuleAvailability(BACKEND_MODULE_ID, productMode.providesBackendModule)

    val platformSplit = PluginModuleId("intellij.platform.split", PluginModuleId.JETBRAINS_NAMESPACE)
    val backendSplit = PluginModuleId("intellij.platform.backend.split", PluginModuleId.JETBRAINS_NAMESPACE)
    setModuleAvailability(backendSplit, productMode == ProductMode.BACKEND)

    val frontendSplitBase = PluginModuleId("intellij.platform.frontend.split.base", PluginModuleId.JETBRAINS_NAMESPACE)
    val frontendSplit = PluginModuleId("intellij.platform.frontend.split", PluginModuleId.JETBRAINS_NAMESPACE)
    when {
      productMode.isLight -> {
        val rpc = PluginModuleId("intellij.platform.rpc", PluginModuleId.JETBRAINS_NAMESPACE)
        val debugger = PluginModuleId("intellij.platform.debugger", PluginModuleId.JETBRAINS_NAMESPACE)
        val platformSplitConnection = PluginModuleId("intellij.platform.split.connection", PluginModuleId.JETBRAINS_NAMESPACE)
        val rdClient = PluginModuleId("intellij.rd.client", PluginModuleId.JETBRAINS_NAMESPACE)
        val cwmPluginCommon = PluginModuleId("intellij.cwm.plugin.common", PluginModuleId.JETBRAINS_NAMESPACE)

        setModuleAvailability(frontendSplitBase, true)

        for (moduleId in listOf(frontendSplit, platformSplit, rpc, rdClient, cwmPluginCommon)) {
          setModuleAvailability(moduleId, false)
        }
        val enableDebugger = SystemProperties.getBooleanProperty("intellij.platform.light.mode.enable.debugger", false)
        setModuleAvailability(debugger, enableDebugger)

        for (moduleId in listOf(platformSplitConnection)) {
          setModuleAvailability(moduleId, productMode == ProductMode.LIGHT_WITH_RD_CONNECTION)
        }
      }
      else -> {
        setModuleAvailability(platformSplit, productMode == ProductMode.FRONTEND || productMode == ProductMode.BACKEND)
        setModuleAvailability(frontendSplitBase, productMode == ProductMode.FRONTEND)
        setModuleAvailability(frontendSplit, productMode == ProductMode.FRONTEND)
      }
    }

    if (productMode != ProductMode.LANGUAGE_SERVER) { // this condition is a workaround for LSP-1549
      val backendJpsGraph = PluginModuleId("intellij.platform.jps.build.dependencyGraph", PluginModuleId.JETBRAINS_NAMESPACE)
      setModuleAvailability(backendJpsGraph, productMode.providesBackendModule)
    }
  }

  private val REMOTE_DEVELOPMENT_PLUGIN_ID: PluginId = PluginId.getId("com.jetbrains.remoteDevelopment")
}
