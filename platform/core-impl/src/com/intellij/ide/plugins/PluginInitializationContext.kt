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
   * Produces a sequence of modules that should be deemed as additional dependencies of a given [descriptor].
   * Note that the generated dependency is "strict", meaning that if the target gets excluded (e.g., if the target is a plugin that is marked disabled),
   * then [descriptor] will also be excluded.
   *
   * Called for all possible modules and "depends" sub-descriptors independently.
   *
   * TODO Ideally, [pluginSet] should not be used, but it's required in the current [ProductPluginInitContext] implementation.
   *
   * @see [provideCompatibilityDependenciesForRemainingCandidates]
   */
  fun provideCompatibilityDependencies(descriptor: IdeaPluginDescriptorImpl, pluginSet: UnambiguousPluginSet): Sequence<DependencyRef>

  /**
   * This method is different from [provideCompatibilityDependencies] in that it allows generating "soft" compatibility dependencies:
   * imagine that several modules were extracted from the IDE's core and now form a separate plugin that can be disabled.
   * Previously, these modules were available to external plugins via the Core classloader, i.e. without any explicit dependency,
   * but now they are not available without an explicit dependency, which breaks compatibility.
   * To remedy this, we want to supply additional dependencies on extracted modules. Producing a "strict" dependency
   * (as in [provideCompatibilityDependencies]) may sometimes be too strict, e.g., if that new extracted plugin is disabled, external plugins
   * that receive such a compatibility dependency (even those that don't actually need it) will be excluded since the dependency is "strict".
   * However, this method is called when the preliminary set of remaining candidates is already constructed, i.e. when all regular module
   * exclusion rules are processed, and it allows skipping generation of compatibility dependencies if the dependency target is already excluded.
   *
   * This method is called for every remaining candidate descriptor.
   *
   * Dependencies produced by this method bypass content module visibility checks. This is intentional: the method is a product-level compatibility
   * mechanism which may restore access that existed before a module was extracted. Implementations are responsible for only providing dependencies
   * for which bypassing visibility is appropriate.
   *
   * Note that producing additional dependencies here still may cause exclusions (e.g., if a dependency cycle appears).
   *
   * Note that eventually every implicit dependency that is added through this method should become explicit in the affected plugins.
   * This method should only work as a temporary compatibility mechanism, it should not grow indefinitely.
   */
  fun provideCompatibilityDependenciesForRemainingCandidates(descriptor: IdeaPluginDescriptorImpl, remainingCandidates: RemainingCandidatesView): Sequence<DependencyRef>

  interface RemainingCandidatesView {
    fun resolvePluginId(id: PluginId): PluginModuleDescriptor?
    fun resolveContentModuleId(id: PluginModuleId): ContentModuleDescriptor?
  }

  fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusionReason>>

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
  PluginCompatibilityUtils.checkBuildNumberCompatibility(plugin, productBuildNumber)?.let {
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

@ApiStatus.Internal
fun PluginInitializationContext.RemainingCandidatesView.resolveReference(ref: DependencyRef): PluginModuleDescriptor? {
  return when (ref) {
    is DependencyRef.Plugin -> resolvePluginId(ref.pluginId)
    is DependencyRef.ContentModule -> resolveContentModuleId(ref.moduleId)
  }
}
