// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ApplicationInfoMarketplaceCustomizationService : MarketplaceCustomizationService {
  override fun getPluginManagerUrl() = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl

  override fun getPluginDownloadUrl() = ApplicationInfoImpl.getShadowInstance().pluginDownloadUrl

  override fun getPluginsListUrl() = ApplicationInfoImpl.getShadowInstance().pluginsListUrl

  override fun getPluginHomepageUrl(pluginId: PluginId) =
    "${getPluginManagerUrl()}/plugin/index?xmlId=${URLUtil.encodeURIComponent(pluginId.idString)}"

}