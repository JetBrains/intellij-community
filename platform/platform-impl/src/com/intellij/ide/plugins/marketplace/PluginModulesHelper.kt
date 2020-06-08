// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

open class PluginModulesHelper {
  companion object {
    private val INSTANCE = PluginModulesHelper()

    @JvmStatic
    fun getInstance(): PluginModulesHelper = INSTANCE
  }

  fun getMarketplacePluginIdByModule(depPluginId: PluginId): PluginId? {
    val installedPluginWithModule = PluginManagerCore.findPluginByModuleDependency(depPluginId)
    return if (installedPluginWithModule != null) {
      installedPluginWithModule.pluginId
    }
    else {
      val updatesByModule: List<IdeCompatibleUpdate> = MarketplaceRequests.getInstance().getCompatibleUpdatesByModule(depPluginId.idString)
      updatesByModule.firstOrNull()?.let { PluginId.getId(it.pluginId) }
    }
  }

  fun getInstalledPluginIdByModule(depPluginId: PluginId): PluginId? {
    return PluginManagerCore.findPluginByModuleDependency(depPluginId)?.pluginId
  }


}