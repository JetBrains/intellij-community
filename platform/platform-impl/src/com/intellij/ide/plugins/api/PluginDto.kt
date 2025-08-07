// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNodeVendorDetails
import com.intellij.ide.plugins.newui.PluginDependencyModel
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
@IntellijInternalApi
class PluginDto(
  override var name: String? = null,
  override var pluginId: PluginId,
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
  override var source: PluginSource? = null
  override var dependencies: MutableList<PluginDependencyModel> = mutableListOf()
  override var dependencyNames: Collection<String>? = null
  override var suggestedFeatures: Collection<String> = emptyList()
  override var vendor: String? = null
  override var organization: String? = null
  override var changeNotes: String? = null
  override var productCode: String? = null
  override var releaseDate: Long? = null
  override var size: String? = null
  override var downloadUrl: String? = null

  override var releaseVersion: Int = 0
  override var displayCategory: String? = null
  override var isImplementationDetail: Boolean = false
  override var vendorDetails: PluginNodeVendorDetails? = null
  override var reviewComments: ReviewsPageContainer? = null

  override var forumUrl: String? = null
  override var licenseUrl: String? = null
  override var bugtrackerUrl: String? = null
  override var documentationUrl: String? = null
  override var sourceCodeUrl: String? = null
  override var reportPluginUrl: String? = null
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

  override var sinceBuild: String? = null
  override var isBundledUpdate: Boolean = false
  override var untilBuild: String? = null

  override var isDisableAllowed: Boolean = true

  override fun getDescriptor(): IdeaPluginDescriptor {
    return PluginDtoDescriptorWrapper(this)
  }

  override fun addDependency(id: PluginId, optional: Boolean) {
    dependencies.add(PluginDependencyModel(id, optional))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginDto

    return pluginId == other.pluginId
  }

  override fun hashCode(): Int {
    return pluginId.hashCode()
  }

  companion object {
    @JvmStatic
    fun fromModel(model: PluginUiModel): PluginDto {
      if (model is PluginDto) {
        return model
      }

      return PluginDto(model.name, model.pluginId).apply {
        version = model.version
        isBundled = model.isBundled
        isDeleted = model.isDeleted
        isIncompatibleWithCurrentOs = model.isIncompatibleWithCurrentOs
        isIncompatible = model.isIncompatible
        canBeEnabled = model.canBeEnabled
        requiresUpgrade = model.requiresUpgrade
        isFromMarketplace = model.isFromMarketplace
        isLicenseOptional = model.isLicenseOptional
        isConverted = model.isConverted
        detailsLoaded = model.detailsLoaded
        allowBundledUpdate = model.allowBundledUpdate
        isPaid = model.isPaid
        source = model.source
        dependencies = model.dependencies.toMutableList()
        dependencyNames = model.dependencyNames?.toList()
        suggestedFeatures = model.suggestedFeatures.toList()
        vendor = model.vendor
        organization = model.organization
        changeNotes = model.changeNotes
        productCode = model.productCode
        size = model.size
        releaseVersion = model.releaseVersion
        displayCategory = model.displayCategory
        forumUrl = model.forumUrl
        licenseUrl = model.licenseUrl
        bugtrackerUrl = model.bugtrackerUrl
        documentationUrl = model.documentationUrl
        sourceCodeUrl = model.sourceCodeUrl
        reportPluginUrl = model.reportPluginUrl
        vendorDetails = model.vendorDetails
        reviewComments = model.reviewComments
        screenShots = model.screenShots?.toList()
        externalPluginIdForScreenShots = model.externalPluginIdForScreenShots
        downloadUrl = model.downloadUrl
        externalPluginId = model.externalPluginId
        externalUpdateId = model.externalUpdateId
        defaultTrialPeriod = model.defaultTrialPeriod
        customTrialPeriods = model.customTrialPeriods?.toMap()
        date = model.date
        isEnabled = model.isEnabled
        tags = model.tags?.toList()
        suggestedCommercialIde = model.suggestedCommercialIde
        downloads = model.downloads
        rating = model.rating
        repositoryName = model.repositoryName
        channel = model.channel
        installSource = model.installSource
        description = model.description
        category = model.category
        sinceBuild = model.sinceBuild
        untilBuild = model.untilBuild
        releaseDate = model.releaseDate
        isBundledUpdate = model.isBundledUpdate
        isImplementationDetail = model.isImplementationDetail
        isDisableAllowed = model.isDisableAllowed
      }
    }
  }
}