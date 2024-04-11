// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.IDE_BUILD_FOR_REQUEST
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.URLUtil

class ApplicationInfoMarketplaceCustomizationService : MarketplaceCustomizationService {
  override fun getPluginManagerUrl() = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl

  override fun getPluginDownloadUrl() = ApplicationInfoImpl.getShadowInstance().pluginDownloadUrl

  override fun getPluginsListUrl() = ApplicationInfoImpl.getShadowInstance().pluginsListUrl

  override fun getPluginHomepageUrl(pluginId: PluginId) =
    "${getPluginManagerUrl()}/plugin/index?xmlId=${pluginId.urlEncode()}"

  override fun getPluginReviewsUrl(pluginId: PluginId, page: Int): String {
    val pageValue = if (page == 1) "" else "?page=$page"
    return "${MarketplaceUrls.getPluginManagerUrl()}/api/products/intellij/plugins/${pluginId.urlEncode()}/comments$pageValue"
  }

  override fun getPluginWriteReviewUrl(pluginId: PluginId, version: String?) = buildString {
    append("${MarketplaceUrls.getPluginManagerUrl()}/intellij/${pluginId.urlEncode()}/review/new")
    append("?build=$IDE_BUILD_FOR_REQUEST")
    version?.let {
      append("&version=$it")
    }
  }

  private fun PluginId.urlEncode() = URLUtil.encodeURIComponent(idString)
}