// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.application.impl.ApplicationInfoImpl

class ApplicationInfoMarketplaceCustomizationService: MarketplaceCustomizationService {
  override fun getPluginManagerUrl() = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl

  override fun getPluginDownloadUrl() = ApplicationInfoImpl.getShadowInstance().pluginDownloadUrl

  override fun getPluginsListUrl() = ApplicationInfoImpl.getShadowInstance().pluginsListUrl

  override fun pluginHomepageSupported(): Boolean = true

}