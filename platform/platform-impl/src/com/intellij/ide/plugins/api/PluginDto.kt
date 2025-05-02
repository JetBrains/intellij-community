// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.newui.PluginDependencyModel
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.Date


@Serializable
@ApiStatus.Internal
class PluginDto(
  override var name: String? = null,
  override val pluginId: PluginId,
) : PluginUiModel {

  override var version: String? = null
  override var isBundled: Boolean = false
  override var isDeleted: Boolean = false
  override var isIncompatibleWithCurrentOs: Boolean = false
  override var isIncompatible: Boolean = false
  override var canBeEnabled: Boolean = true
  override var requiresUpgrade: Boolean = false
  override var isFromMarketplace: Boolean = false
  override var isLicenseOptional: Boolean = false
  override var isConverted: Boolean = false
  override var detailsLoaded: Boolean = false
  override var allowBundledUpdate: Boolean = false
  override var isPaid: Boolean = false
  override var source: PluginSource = PluginSource.LOCAL
  override var dependencies: MutableList<PluginDependencyModel> = mutableListOf()
  override var dependencyNames: Collection<String>? = null
  override var suggestedFeatures: Collection<String> = emptyList()
  override var vendor: String? = null
  override var organization: String? = null
  override var changeNotes: String? = null
  override var productCode: String? = null
  override var size: String? = null
  override var downloadUrl: String? = null

  override var releaseVersion: Int = 0
  override var displayCategory: String? = null
  override var reviewComments: ReviewsPageContainer? = null

  // Mutable properties
  override var forumUrl: String? = null
  override var licenseUrl: String? = null
  override var bugtrackerUrl: String? = null
  override var documentationUrl: String? = null
  override var sourceCodeUrl: String? = null
  override var reportPluginUrl: String? = null
  override var verifiedName: String? = null
  override var isVerified: Boolean = false
  override var isTrader: Boolean = false
  override var screenShots: List<String>? = null
  override var externalPluginIdForScreenShots: String? = null
  override var externalPluginId: String? = null
  override var externalUpdateId: String? = null
  override var defaultTrialPeriod: Int? = null
  override var customTrialPeriods: Map<String, Int>? = null
  override var date: Long = 0
  override var isEnabled: Boolean = true
  override var tags: List<String>? = null
  override var suggestedCommercialIde: String? = null
  override var downloads: String? = null
  override var rating: String? = null
  override var repositoryName: String? = null
  override var channel: String? = null
  override var installSource: FUSEventSource? = null

  override var category: String? = null
  override var description: String? = null

  override fun addDependency(id: PluginId, optional: Boolean) {
    dependencies.add(PluginDependencyModel(id, optional))
  }
}