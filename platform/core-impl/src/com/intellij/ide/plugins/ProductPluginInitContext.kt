// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProductPluginInitContext(
  private val buildNumberOverride: BuildNumber? = null,
  private val disabledPluginsOverride: Set<PluginId>? = null,
  private val expiredPluginsOverride: Set<PluginId>? = null,
  private val brokenPluginVersionsOverride: Map<PluginId, Set<String>>? = null,
) : PluginInitializationContext {
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
}