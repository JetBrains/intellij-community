// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginInitializationContext.EnvironmentConfiguredModuleData
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
class ProductPluginInitContext(
  private val buildNumberOverride: BuildNumber? = null,
  private val disabledPluginsOverride: Set<PluginId>? = null,
  private val expiredPluginsOverride: Set<PluginId>? = null,
  private val brokenPluginVersionsOverride: Map<PluginId, Set<String>>? = null,
) : PluginInitializationContext {
  override val essentialPlugins: Set<PluginId> by lazy {
    buildSet {
      add(PluginManagerCore.CORE_ID)
      addAll(ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds())
    }
  }
  private val disabledPlugins: Set<PluginId> by lazy { disabledPluginsOverride ?: DisabledPluginsState.getDisabledIds() }
  private val expiredPlugins: Set<PluginId> by lazy { expiredPluginsOverride ?: ExpiredPluginsState.expiredPluginIds }
  private val brokenPluginVersions: Map<PluginId, Set<String>> by lazy { brokenPluginVersionsOverride ?: getBrokenPluginVersions() }

  override val productBuildNumber: BuildNumber
    get() = buildNumberOverride ?: PluginManagerCore.buildNumber

  override fun isPluginDisabled(id: PluginId): Boolean {
    return PluginManagerCore.CORE_ID != id && disabledPlugins.contains(id)
  }

  override fun isPluginBroken(id: PluginId, version: String?): Boolean {
    val set = brokenPluginVersions[id] ?: return false
    return set.contains(version)
  }

  override fun isPluginExpired(id: PluginId): Boolean = expiredPlugins.contains(id)

  override val requirePlatformAliasDependencyForLegacyPlugins: Boolean
    get() = !PlatformUtils.isIntelliJ()

  override val checkEssentialPlugins: Boolean
    get() = !PluginManagerCore.isUnitTestMode

  override val explicitPluginSubsetToLoad: Set<PluginId>? by lazy {
    System.getProperty("idea.load.plugins.id")
      ?.splitToSequence(',')
      ?.filter { it.isNotEmpty() }
      ?.map(PluginId::getId)
      ?.toHashSet()
  }

  override val disablePluginLoadingCompletely: Boolean
    get() = !System.getProperty("idea.load.plugins", "true").toBoolean()

  override val pluginsPerProjectConfig: PluginsPerProjectConfig? by lazy {
    if (java.lang.Boolean.getBoolean("ide.per.project.instance")) {
      PluginsPerProjectConfig(
        isMainProcess = !PathManager.getPluginsDir().fileName.toString().startsWith("perProject_")
      )
    }
    else null
  }

  override val currentProductModeId: String
    get() = ProductLoadingStrategy.strategy.currentModeId

  override val environmentConfiguredModules: Map<PluginModuleId, EnvironmentConfiguredModuleData> by lazy {
    buildMap {
      configureProductModeModules(currentProductModeId)
    }
  }

  companion object {
    @VisibleForTesting
    internal fun MutableMap<PluginModuleId, EnvironmentConfiguredModuleData>.configureProductModeModules(productModeId: String) {
      val frontendSplit = PluginModuleId("intellij.platform.frontend.split", PluginModuleId.JETBRAINS_NAMESPACE)
      val frontend = PluginModuleId("intellij.platform.frontend", PluginModuleId.JETBRAINS_NAMESPACE)
      val backend = PluginModuleId("intellij.platform.backend", PluginModuleId.JETBRAINS_NAMESPACE)
      val backendJps = PluginModuleId("intellij.platform.jps.build", PluginModuleId.JETBRAINS_NAMESPACE)
      val backendJpsGraph = PluginModuleId("intellij.platform.jps.build.dependencyGraph", PluginModuleId.JETBRAINS_NAMESPACE)

      for (moduleId in listOf(frontend, backend, frontendSplit, backendJps, backendJpsGraph)) {
        val isAvailable = when (productModeId) {
          /** intellij.platform.backend.split is currently available in 'monolith' mode because it's used as a backend in CodeWithMe */
          "monolith" -> moduleId != frontendSplit
          "backend" -> moduleId != frontend && moduleId != frontendSplit
          "frontend" -> moduleId != backend && moduleId != backendJps && moduleId != backendJpsGraph
          else -> true
        }
        val unavailabilityReason =
          if (isAvailable) null
          else UnsuitableProductModeModuleUnavailabilityReason(moduleId, productModeId)
        val replaced = put(moduleId, EnvironmentConfiguredModuleData(unavailabilityReason))
        check(replaced == null) { "$moduleId is already registered as environment-configured module" }
      }
    }
  }
}