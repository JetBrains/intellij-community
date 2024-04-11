// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

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
   * @param pluginId the identifier of the plugin
   *
   * @return the address of a plugin homepage if the current customization supports it,
   * `null` otherwise
   */
  @Nullable
  fun getPluginHomepageUrl(pluginId: PluginId): String?

  /**
   * Returns the URL of the page with reviews for a specific plugin and page number.
   *
   * @param pluginId the identifier of the plugin
   * @param page the page number of the reviews to be shown
   *
   * @return the URL of the page with reviews if current customization supports them, `null` otherwise
   */
  @Nullable
  fun getPluginReviewsUrl(pluginId: PluginId, page: Int): String?

  /**
   * Returns the URL for writing a review for a specific plugin.
   *
   * @param pluginId the identifier of the plugin
   * @param version the version of the plugin (optional)
   *
   * @return the URL for writing a review if current customization supports it, null otherwise
   */
  @Nullable
  fun getPluginWriteReviewUrl(pluginId: PluginId, version: String? = null): String?

}