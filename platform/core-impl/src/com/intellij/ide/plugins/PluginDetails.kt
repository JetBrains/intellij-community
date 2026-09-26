// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Exposes information about plugins currently known to the IDE.
 *
 * Prefer this API over direct usage of [PluginManagerCore] when client code only needs to inspect plugin metadata.
 *
 * @see com.intellij.openapi.application.PluginPathManager to load plugin resources
 * @see com.intellij.openapi.updateSettings.PluginUpdateCheckService to check for updates in the Marketplace
 */
@ApiStatus.Experimental
@Service(Service.Level.APP)
class PluginDetailsService {
  companion object {
    @JvmStatic
    fun getInstance(): PluginDetailsService = service<PluginDetailsService>()
  }

  /**
   * Returns details for all currently active (loaded) plugins.
   */
  fun getActivePlugins(): Sequence<PluginDetails> {
    return PluginManagerCore.loadedPlugins.asSequence()
      .filterNot { it.pluginId == PluginManagerCore.CORE_ID }
      .map { it.toPluginDetails() }
  }

  /**
   * Returns [PluginDetails] for the plugin with the given [pluginId], or `null` if no such plugin is known.
   */
  fun findDetails(pluginId: PluginId): PluginDetails? {
    return PluginManagerCore.getPlugin(pluginId)?.toPluginDetails()
  }

  /**
   * Returns `true` if the plugin with the given [pluginId] is loaded.
   */
  fun isLoaded(pluginId: PluginId): Boolean = PluginManagerCore.isLoaded(pluginId)

  /**
   * Returns `true` if the plugin with the given [pluginId] is marked as disabled by the user.
   */
  fun isDisabled(pluginId: PluginId): Boolean = PluginManagerCore.isDisabled(pluginId)

  /**
   * Returns `true` if the plugin with the given [pluginId] is part of the IDE installation,
   * either as a bundled plugin or as an update of a bundled plugin.
   */
  fun isBuiltIn(pluginId: PluginId): Boolean {
    val plugin = PluginManagerCore.getPlugin(pluginId) ?: return false
    return isBundledOrBundledUpdatedPlugin(plugin)
  }

  /**
   * Vendor information of a plugin.
   *
   * @see PluginDetails.vendor
   */
  @ApiStatus.Experimental
  class PluginVendorInfo internal constructor(
    val name: @NlsSafe String?,
    val email: String?,
    val url: String?,
    val organization: @NlsSafe String?,
  ) {
    override fun toString(): String {
      return "PluginVendorInfo(name=$name)"
    }
  }

  /**
   * A declared dependency of a plugin on another module.
   *
   * A dependency on a plugin in fact means a dependency on the *implicit main module* of that plugin
   * and is represented by [OnPlugin]. A dependency on a content module is represented by [OnModule].
   *
   * @see PluginDetails.dependencies
   */
  @ApiStatus.Experimental
  sealed interface ModuleDependencyInfo {
    /**
     * A dependency on the implicit main module of another plugin, identified by [pluginId].
     */
    @ApiStatus.Experimental
    class OnPlugin internal constructor(
      val pluginId: PluginId,
    ) : ModuleDependencyInfo

    /**
     * A dependency on a content module of another plugin, identified by its module [name].
     */
    @ApiStatus.Experimental
    class OnModule internal constructor(
      val name: String,
    ) : ModuleDependencyInfo
  }

  /**
   * A content module that belongs to a plugin.
   *
   * @see PluginDetails.modules
   */
  @ApiStatus.Experimental
  class PluginModuleInfo internal constructor(
    val name: String,
  ) {
    override fun toString(): String {
      return "PluginModuleInfo(name='$name')"
    }
  }

  /**
   * An immutable snapshot of metadata about a plugin.
   *
   * Use [PluginDetailsService] to obtain instances of this class; do not construct them directly.
   */
  @ApiStatus.Experimental
  class PluginDetails internal constructor(
    val id: PluginId,
    val name: @NlsSafe String,
    val description: @Nls String?,
    val version: String?,
    val changeNotes: String?,
    val vendor: PluginVendorInfo,
    val modules: Collection<PluginModuleInfo>,
    /**
     * All non-optional plugin dependencies, required to load it.
     */
    val dependencies: Collection<ModuleDependencyInfo>,
    /**
     * `true` if the plugin is part of the IDE installation, either as a bundled plugin
     * or as an update of a bundled plugin.
     */
    val isBuiltIn: Boolean,
    /**
     * The release version of a paid or freemium plugin as declared in its `<product-descriptor>`,
     * or `0` if the plugin does not declare one.
     */
    val releaseVersion: Int,
    /**
     * The release date of a paid or freemium plugin as declared in its `<product-descriptor>`,
     * or `null` if the plugin does not declare one.
     */
    val releaseDate: LocalDate?,
    /**
     * `true` if the plugin can be used without a license (i.e. it is a freemium plugin).
     */
    val isLicenseOptional: Boolean,
  ) {
    override fun toString(): String {
      return "PluginDetails(id=$id, name='$name')"
    }
  }

  private fun isBundledOrBundledUpdatedPlugin(plugin: IdeaPluginDescriptor): Boolean {
    return plugin.isBundled || PluginManagerCore.isUpdatedBundledPlugin(plugin)
  }

  private fun IdeaPluginDescriptor.toPluginDetails(): PluginDetails {
    val modules: List<PluginModuleInfo> = if (this is PluginMainDescriptor) {
      contentModules.map { PluginModuleInfo(it.moduleId.name) }
    }
    else {
      emptyList()
    }

    val dependencies = mutableListOf<ModuleDependencyInfo>()
    for (dep in getDependencies()) {
      if (!dep.isOptional) {
        dependencies.add(ModuleDependencyInfo.OnPlugin(dep.pluginId))
      }
    }

    if (this is IdeaPluginDescriptorImpl) {
      for (pluginId in moduleDependencies.plugins) {
        dependencies.add(ModuleDependencyInfo.OnPlugin(pluginId))
      }
      for (moduleId in moduleDependencies.modules) {
        dependencies.add(ModuleDependencyInfo.OnModule(moduleId.name))
      }
    }

    return PluginDetails(
      id = pluginId,
      name = name,
      description = description,
      version = version,
      changeNotes = changeNotes,
      vendor = PluginVendorInfo(
        name = vendor,
        email = vendorEmail,
        url = vendorUrl,
        organization = organization,
      ),
      modules = modules,
      dependencies = dependencies,
      isBuiltIn = isBundledOrBundledUpdatedPlugin(this),
      releaseVersion = releaseVersion,
      releaseDate = releaseDate?.toInstant()?.atZone(ZoneOffset.UTC)?.toLocalDate(),
      isLicenseOptional = isLicenseOptional,
    )
  }
}
