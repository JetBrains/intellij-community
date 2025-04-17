// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginInitializationContext {
  val productBuildNumber: BuildNumber
  fun isPluginDisabled(id: PluginId): Boolean
  fun isPluginExpired(id: PluginId): Boolean
  fun isPluginBroken(id: PluginId, version: String?): Boolean

  @ApiStatus.Internal
  companion object {
    fun build(
      disabledPlugins: Set<PluginId>,
      expiredPlugins: Set<PluginId>,
      brokenPluginVersions: Map<PluginId, Set<String?>>,
      getProductBuildNumber: () -> BuildNumber,
    ): PluginInitializationContext =
      object : PluginInitializationContext {
        override val productBuildNumber: BuildNumber get() = getProductBuildNumber()
        override fun isPluginDisabled(id: PluginId): Boolean = id in disabledPlugins
        override fun isPluginExpired(id: PluginId): Boolean = id in expiredPlugins
        override fun isPluginBroken(id: PluginId, version: String?): Boolean = brokenPluginVersions[id]?.contains(version) == true
      }
  }
}