// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarketplaceTextualFeaturesProvider {
  companion object {
    val EP_NAME: ExtensionPointName<MarketplaceTextualFeaturesProvider> = ExtensionPointName.create("com.intellij.marketplaceTextualFeaturesProvider")

    @JvmStatic
    fun getInstanceIfEnabled(): MarketplaceTextualFeaturesProvider? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }

  fun getFeaturesDefinition(): Array<EventField<*>>

  fun getTextualFeatures(query: String, match: String): List<EventPair<*>>
}