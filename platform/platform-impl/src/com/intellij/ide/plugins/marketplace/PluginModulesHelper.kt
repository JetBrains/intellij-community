// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import java.util.*
import java.util.concurrent.TimeUnit

open class PluginModulesHelper {
  companion object {
    @JvmStatic
    fun getInstance(): PluginModulesHelper = service()
  }

  private val pluginsModuleCache: Cache<PluginModule, Optional<PluginId>> = CacheBuilder
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build()

  fun getMarketplacePluginIdByModule(depPluginId: PluginId): PluginId? {
    val installedPluginWithModule = PluginManagerCore.findPluginByModuleDependency(depPluginId)
    if (installedPluginWithModule != null) return installedPluginWithModule.pluginId

    val pluginModule = depPluginId.idString
    val cachedModule = pluginsModuleCache.getIfPresent(pluginModule)?.orElse(null)
    if (cachedModule != null) return cachedModule

    val updatesByModule: List<IdeCompatibleUpdate> = MarketplaceRequests.getInstance().getCompatibleUpdatesByModule(pluginModule)
    return updatesByModule.firstOrNull()
      ?.let { PluginId.getId(it.pluginId) }
      ?.also { pluginsModuleCache.put(pluginModule, Optional.ofNullable(it)) }
  }

  fun getInstalledPluginIdByModule(depPluginId: PluginId): PluginId? {
    return PluginManagerCore.findPluginByModuleDependency(depPluginId)?.pluginId
  }

}

private typealias PluginModule = String