// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.getInstance
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator

internal class PluginInfoProviderImpl : PluginInfoProvider {
  override fun loadCachedPlugins(): List<PluginId>? {
    val pluginsIds = getInstance().getMarketplaceCachedPlugins() ?: return null
    return pluginsIds.map { PluginId.getId(it) }
  }

  override fun loadPlugins(indicator: ProgressIndicator?): List<PluginId> {
    return getInstance().getMarketplacePlugins(indicator).map { PluginId.getId(it) }
  }
}