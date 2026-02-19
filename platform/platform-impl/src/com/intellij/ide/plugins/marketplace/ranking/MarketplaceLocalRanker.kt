// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.ranking

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
interface MarketplaceLocalRanker {
  companion object {
    val EP_NAME: ExtensionPointName<MarketplaceLocalRanker> = ExtensionPointName.create("com.intellij.marketplaceLocalRanker")

    @JvmStatic
    fun getInstanceIfEnabled(): MarketplaceLocalRanker? {
      return EP_NAME.extensionList.firstOrNull()?.takeIf { it.isEnabled() }
    }
  }

  /**
   * Indicates whether machine learning in Marketplace is enabled.
   * This method can return false if ML-ranking is disabled and no experiments are allowed
   */
  fun isEnabled(): Boolean

  /**
   * Ranks the plugins inplace within a single lookup in the Marketplace tab of Plugin Manager.
   * Returns the plugin relevance scores assigned by the ranking model.
   */
  fun rankPlugins(queryParser: SearchQueryParser.Marketplace, plugins: MutableList<PluginUiModel>): Map<PluginUiModel, Double>

  val experimentGroup: Int
  val experimentVersion: Int
}