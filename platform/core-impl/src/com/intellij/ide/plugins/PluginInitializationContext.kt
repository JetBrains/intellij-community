// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.configureProductModeModules
import com.intellij.idea.AppMode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface PluginInitializationContext {
  val productBuildNumber: BuildNumber
  val essentialPlugins: Set<PluginId>
  fun isPluginDisabled(id: PluginId): Boolean
  fun isPluginExpired(id: PluginId): Boolean
  fun isPluginBroken(id: PluginId, version: String?): Boolean

  /**
   * https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
   * If a plugin does not include any platform alias dependency tags in its plugin.xml,
   * it's assumed to be a legacy plugin and is loaded only in IntelliJ IDEA.
   *
   * @see [PluginCompatibilityUtils.isLegacyPluginWithoutPlatformAliasDependencies]
   */
  val requirePlatformAliasDependencyForLegacyPlugins: Boolean

  // TODO: check if this can be dropped (by merging with essentialPlugins somehow)
  val checkEssentialPlugins: Boolean

  /**
   * If not null, plugins that are not listed here or in essential plugins (and their required dependencies) will be excluded from loading.
   *
   * Note: currently, takes precedence over [disablePluginLoadingCompletely], but it should not be relied upon.
   */
  val explicitPluginSubsetToLoad: Set<PluginId>?

  /**
   * If true, only the CORE plugin will be loaded
   */
  val disablePluginLoadingCompletely: Boolean

  val pluginsPerProjectConfig: PluginsPerProjectConfig?

  /**
   * Returns ID of the current [com.intellij.platform.runtime.product.ProductMode]
   */
  val currentProductModeId: String

  /**
   * A map consisting of special modules that are configured by the environment (app mode, OS-specific modules, etc.).
   * If a module is in this map, it is considered to be special and its state is determined
   * by [EnvironmentConfiguredModuleData] rather than by normal plugin/module loading rules.
   */
  val environmentConfiguredModules: Map<PluginModuleId, EnvironmentConfiguredModuleData>

  class EnvironmentConfiguredModuleData(val unavailabilityReason: EnvironmentDependentModuleUnavailabilityReason?) {
    val isAvailable: Boolean get() = unavailabilityReason == null
  }

  @ApiStatus.Internal
  companion object {
    @TestOnly
    fun buildForTest(
      essentialPlugins: Set<PluginId>,
      disabledPlugins: Set<PluginId>,
      expiredPlugins: Set<PluginId>,
      brokenPluginVersions: Map<PluginId, Set<String?>>,
      getProductBuildNumber: () -> BuildNumber,
      requirePlatformAliasDependencyForLegacyPlugins: Boolean,
      checkEssentialPlugins: Boolean,
      explicitPluginSubsetToLoad: Set<PluginId>?,
      disablePluginLoadingCompletely: Boolean,
      currentProductModeId: String,
    ): PluginInitializationContext =
      object : PluginInitializationContext {
        override val productBuildNumber: BuildNumber get() = getProductBuildNumber()
        override val essentialPlugins: Set<PluginId> = essentialPlugins
        override fun isPluginDisabled(id: PluginId): Boolean = id in disabledPlugins
        override fun isPluginExpired(id: PluginId): Boolean = id in expiredPlugins
        override fun isPluginBroken(id: PluginId, version: String?): Boolean = brokenPluginVersions[id]?.contains(version) == true
        override val requirePlatformAliasDependencyForLegacyPlugins: Boolean = requirePlatformAliasDependencyForLegacyPlugins
        override val checkEssentialPlugins: Boolean = checkEssentialPlugins
        override val explicitPluginSubsetToLoad: Set<PluginId>? = explicitPluginSubsetToLoad
        override val disablePluginLoadingCompletely: Boolean = disablePluginLoadingCompletely
        override val pluginsPerProjectConfig: PluginsPerProjectConfig? = null
        override val currentProductModeId: String = currentProductModeId
        override val environmentConfiguredModules: Map<PluginModuleId, EnvironmentConfiguredModuleData> = buildMap {
          configureProductModeModules(currentProductModeId)
        }
      }
  }
}

@ApiStatus.Internal
fun PluginInitializationContext.validatePluginIsCompatible(plugin: PluginMainDescriptor): PluginNonLoadReason? {
  if (isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(plugin)) {
    // disable plugins which are incompatible with the Kotlin Plugin K1/K2 Modes KTIJ-24797, KTIJ-30474
    val mode = if (isKotlinPluginK1Mode()) CoreBundle.message("plugin.loading.error.k1.mode") else CoreBundle.message("plugin.loading.error.k2.mode")
    return PluginIsIncompatibleWithKotlinMode(plugin, mode)
  }
  if (plugin.isBundled) {
    return null
  }
  if (AppMode.isDisableNonBundledPlugins()) {
    return NonBundledPluginsAreExplicitlyDisabled(plugin)
  }
  PluginManagerCore.checkBuildNumberCompatibility(plugin, productBuildNumber)?.let {
    return it
  }
  // "Show broken plugins in Settings | Plugins so that users can uninstall them and resolve 'Plugin Error' (IDEA-232675)"
  if (isPluginBroken(plugin.pluginId, plugin.version)) {
    return PluginIsMarkedBroken(plugin)
  }
  if (requirePlatformAliasDependencyForLegacyPlugins && PluginCompatibilityUtils.isLegacyPluginWithoutPlatformAliasDependencies(plugin)) {
    return PluginIsCompatibleOnlyWithIntelliJIDEA(plugin)
  }
  return null
}

@ApiStatus.Internal
data class PluginsPerProjectConfig(val isMainProcess: Boolean)