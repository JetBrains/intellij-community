// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull

/**
 * Provides an ability to customize various aspects of interaction with the marketplace,
 * usually defined in *ApplicationInfo.xml file
 */
@ApiStatus.Experimental
interface MarketplaceCustomizationService {

  companion object {
    @JvmStatic
    fun getInstance(): MarketplaceCustomizationService = service()
  }

  /**
   * @see com.intellij.openapi.application.ex.ApplicationInfoEx.getPluginManagerUrl
   */
  @NotNull
  fun getPluginManagerUrl(): String

  /**
   * @see com.intellij.openapi.application.ex.ApplicationInfoEx.getPluginDownloadUrl
   */
  @NotNull
  fun getPluginDownloadUrl(): String

  /**
   * @see com.intellij.openapi.application.ex.ApplicationInfoEx.getPluginsListUrl
   */
  @NotNull
  fun getPluginsListUrl(): String

  /**
   * Indicates whether current marketplace implementation supports showing plugin home page
   */
  @NotNull
  fun pluginHomepageSupported(): Boolean

}