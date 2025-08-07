// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginNodeVendorDetails
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import java.text.SimpleDateFormat
import java.util.*

@ApiStatus.Internal
@IntellijInternalApi
class PluginDtoModelBuilder(pluginId: PluginId) : PluginUiModelBuilder {
  private val resultDto = PluginDto(null, pluginId)

  override fun setId(id: String): PluginDtoModelBuilder {
    resultDto.pluginId = PluginId.getId(id)
    return this
  }

  override fun setName(name: String?): PluginUiModelBuilder {
    resultDto.name = name
    return this
  }

  override fun setVersion(version: String?): PluginUiModelBuilder {
    resultDto.version = version
    return this
  }

  override fun setDescription(description: String?): PluginUiModelBuilder {
    resultDto.description = description
    return this
  }

  override fun setVendor(vendor: String?): PluginUiModelBuilder {
    resultDto.vendor = vendor
    return this
  }

  override fun setProductCode(productCode: String?): PluginUiModelBuilder {
    resultDto.productCode = productCode
    return this
  }

  override fun setCategory(category: String?): PluginUiModelBuilder {
    resultDto.category = category
    return this
  }

  override fun setChangeNotes(changeNotes: String?): PluginUiModelBuilder {
    resultDto.changeNotes = changeNotes
    return this
  }

  override fun setSinceBuild(sinceBuild: String?): PluginUiModelBuilder {
    // PluginDto doesn't have direct sinceBuild field
    return this
  }

  override fun setUntilBuild(untilBuild: String?): PluginUiModelBuilder {
    // PluginDto doesn't have direct untilBuild field
    return this
  }

  override fun setDownloads(downloads: String?): PluginUiModelBuilder {
    resultDto.downloads = downloads
    return this
  }

  override fun setRating(rating: String?): PluginUiModelBuilder {
    resultDto.rating = rating
    return this
  }

  override fun setSize(size: String?): PluginUiModelBuilder {
    resultDto.size = size
    return this
  }

  override fun setVendorEmail(vendorEmail: String?): PluginUiModelBuilder {
    // PluginDto doesn't have direct vendorEmail field
    return this
  }

  override fun setVendorUrl(vendorUrl: String?): PluginUiModelBuilder {
    // PluginDto doesn't have direct vendorUrl field
    return this
  }

  override fun setUrl(url: String?): PluginUiModelBuilder {
    // PluginDto doesn't have direct url field
    return this
  }

  override fun setDownloadUrl(downloadUrl: String?): PluginUiModelBuilder {
    resultDto.downloadUrl = downloadUrl
    return this
  }

  override fun setDate(date: String): PluginUiModelBuilder {
    // Convert string date to long
    try {
      val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      resultDto.date = format.parse(date).time
    }
    catch (e: Exception) {
      // If date parsing fails, leave date as 0
    }
    return this
  }

  override fun setDependencies(dependencies: List<PluginDependencyModel>): PluginUiModelBuilder {
    resultDto.dependencies.clear()
    resultDto.dependencies.addAll(dependencies)
    return this
  }

  override fun addDependency(id: String, optional: Boolean): PluginUiModelBuilder {
    resultDto.addDependency(PluginId.getId(id), optional)
    return this
  }

  override fun addTag(tag: String): PluginUiModelBuilder {
    val currentTags = resultDto.tags?.toMutableList() ?: mutableListOf()
    currentTags.add(tag)
    resultDto.tags = currentTags
    return this
  }

  override fun setIncomplete(incomplete: Boolean): PluginUiModelBuilder {
    // PluginDto doesn't have direct incomplete field
    return this
  }

  override fun setIsConverted(converted: Boolean): PluginUiModelBuilder {
    resultDto.isConverted = converted
    return this
  }

  override fun setIsPaid(isPaid: Boolean): PluginUiModelBuilder {
    resultDto.isPaid = isPaid
    return this
  }

  override fun setIsFromMarketPlace(isFromMarketPlace: Boolean): PluginUiModelBuilder {
    resultDto.isFromMarketplace = isFromMarketPlace
    return this
  }

  override fun setExternalPluginId(externalPluginId: String?): PluginUiModelBuilder {
    resultDto.externalPluginId = externalPluginId
    return this
  }

  override fun setExternalUpdateId(externalUpdateId: String?): PluginUiModelBuilder {
    resultDto.externalUpdateId = externalUpdateId
    return this
  }

  override fun setTags(tags: List<String>?): PluginUiModelBuilder {
    resultDto.tags = tags
    return this
  }

  override fun setDate(date: Long): PluginUiModelBuilder {
    resultDto.date = date
    return this
  }

  override fun setRepositoryName(repositoryName: String): PluginUiModelBuilder {
    resultDto.repositoryName = repositoryName
    return this
  }

  override fun setVendorDetails(organization: String?): PluginUiModelBuilder {
    if (organization != null) {
      resultDto.vendorDetails = PluginNodeVendorDetails(organization)
    }
    return this
  }

  override fun setDisableAllowed(disabledAllowed: Boolean): PluginUiModelBuilder {
    resultDto.isDisableAllowed = disabledAllowed
    return this
  }

  override fun build(): PluginUiModel {
    return resultDto
  }
}

@ApiStatus.Internal
@IntellijInternalApi
object PluginDtoModelBuilderFactory : PluginUiModelBuilderFactory {
  override fun createBuilder(id: PluginId): PluginUiModelBuilder {
    return PluginDtoModelBuilder(id)
  }
}