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
 *
 * These rules are product policy, so they stay here and not on [ProductMode]. [ProductMode] carries
 * only the structural facts of a mode.
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

    val availability = environmentModuleAvailability(
      mode = productMode,
      lightModeDebugger = SystemProperties.getBooleanProperty("intellij.platform.light.mode.enable.debugger", false),
      lightModeExecution = SystemProperties.getBooleanProperty("intellij.platform.light.mode.enable.execution", true),
    )
    for ((moduleName, isAvailable) in availability) {
      setModuleAvailability(PluginModuleId(moduleName, PluginModuleId.JETBRAINS_NAMESPACE), isAvailable)
    }
  }

  /**
   * Returns the availability of each environment-configured module in [mode], in registration order.
   *
   * [lightModeDebugger] and [lightModeExecution] apply only to a light mode.
   */
  private fun environmentModuleAvailability(mode: ProductMode, lightModeDebugger: Boolean, lightModeExecution: Boolean): Map<String, Boolean> {
    val providesBackend = mode == ProductMode.MONOLITH || mode == ProductMode.BACKEND || mode == ProductMode.LANGUAGE_SERVER
    // a monolith process shows the UI, so it provides the frontend module, but it is not a frontend process
    val providesFrontend = mode == ProductMode.MONOLITH || mode == ProductMode.FRONTEND || mode.isLight

    return buildMap {
      this["intellij.platform.frontend"] = providesFrontend
      this["intellij.platform.backend"] = providesBackend
      // temporary marker, see community/platform/backend/baseline/README.md
      this["intellij.platform.backend.baseline"] = providesBackend || mode == ProductMode.LIGHT_MONOLITH
      this["intellij.platform.backend.split"] = mode == ProductMode.BACKEND
      this["intellij.platform.frontend.split.baseline"] = mode.isFrontendProcess
      if (mode.isLight) {
        for (moduleName in listOf("intellij.platform.frontend.split", "intellij.platform.split", "intellij.platform.rpc",
                                  "intellij.rd.client", "intellij.cwm.plugin.common")) {
          this[moduleName] = false
        }
        this["intellij.platform.debugger"] = lightModeDebugger
        // execution is enabled in LIGHT_WITH_RD_CONNECTION, because `intellij.platform.frontend.split.connection` depends on it via `intellij.remoteDev.frontendProcessLauncher`
        this["intellij.platform.execution"] = lightModeExecution || mode == ProductMode.LIGHT_WITH_RD_CONNECTION
        this["intellij.platform.split.connection"] = mode == ProductMode.LIGHT_WITH_RD_CONNECTION
      }
      else {
        this["intellij.platform.split"] = mode == ProductMode.FRONTEND || mode == ProductMode.BACKEND
        this["intellij.platform.frontend.split"] = mode == ProductMode.FRONTEND
      }
      // this condition is a workaround for LSP-1549
      if (mode != ProductMode.LANGUAGE_SERVER) {
        this["intellij.platform.jps.build.dependencyGraph"] = providesBackend
      }
    }
  }

  private val REMOTE_DEVELOPMENT_PLUGIN_ID: PluginId = PluginId.getId("com.jetbrains.remoteDevelopment")
}
