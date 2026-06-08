// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.URLUtil

internal class ApplicationInfoMarketplaceCustomizationService : MarketplaceCustomizationService {
  override fun usesJetBrainsPluginRepository(): Boolean {
    return ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()
  }

  override fun getPluginManagerUrl(): String = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl

  override fun getPluginDownloadUrl(): String = ApplicationInfoImpl.getShadowInstance().pluginDownloadUrl

  override fun getPluginsListUrl(): String = ApplicationInfoImpl.getShadowInstance().pluginsListUrl

  override fun getPluginHomepageUrl(pluginId: PluginId): String =
    "${getPluginManagerUrl()}/plugin/index?xmlId=${URLUtil.encodeURIComponent(pluginId.idString)}"
}