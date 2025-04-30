// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProductPluginInitContext(
  private val buildNumberOverride: BuildNumber? = null,
  private val disabledPluginsOverride: Set<PluginId>? = null,
  private val expiredPluginsOverride: Set<PluginId>? = null,
  private val brokenPluginVersionsOverride: Map<PluginId, Set<String>>? = null,
) : PluginInitializationContext {
  override val essentialPlugins: Set<PluginId> by lazy { ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds().toSet() }
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
}