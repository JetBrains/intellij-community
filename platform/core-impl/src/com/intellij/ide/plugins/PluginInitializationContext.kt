// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.ProductRulesImposedExclusion.ProductRulesImposedExclusionReason
import com.intellij.idea.AppMode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginInitializationContext {
  val productBuildNumber: BuildNumber
  val essentialPlugins: Set<PluginId>
  fun isPluginDisabled(id: PluginId): Boolean
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

  /**
   * Processed for all possible modules and "depends" sub-descriptors independently.
   * @return a sequence of modules that should be deemed as additional dependencies of a given [descriptor].
   *
   * TODO Ideally, [pluginSet] should not be used, but it's required in the current [ProductPluginInitContext] implementation.
   */
  fun provideCompatibilityDependencies(descriptor: IdeaPluginDescriptorImpl, pluginSet: UnambiguousPluginSet): Sequence<DependencyRef>

  fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusionReason>>

  /**
   * Tells the plugin set resolver that [module] should belong to the same [RuntimeModuleGroup] (the same classloader) as the returned result (if not null).
   */
  fun provideCustomRuntimeModuleGroupAffiliation(module: PluginModuleDescriptor, pluginSet: UnambiguousPluginSet): PluginModuleDescriptor?

  /**
   * To preserve compatibility, all "active" `<depends>` dependencies imply extra dependencies on all "active" content modules of the target.
   * This method allows controlling this mechanism.
   * @return `false` if additional edges to content modules should not be generated when there is a `<depends>` edge to the [resolvedTarget].
   */
  fun shouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget: PluginMainDescriptor): Boolean

  /**
   * Only is called once during the startup initialization
   */
  fun runConfigurationDuringStartup(totalPluginSet: AmbiguousPluginSet)

  companion object
}

@ApiStatus.Internal
fun PluginInitializationContext.validatePluginIsCompatible(plugin: PluginMainDescriptor): PluginNonLoadReason? {
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