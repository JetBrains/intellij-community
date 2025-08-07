// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Builder interface for creating PluginUiModel instances.
 * Needed because we will have different implementations for frontend and backend parsing, at least for the first time.
 */
@ApiStatus.Internal
@IntellijInternalApi
interface PluginUiModelBuilder {
  fun setId(id: String): PluginUiModelBuilder
  fun setName(name: String?): PluginUiModelBuilder
  fun setVersion(version: String?): PluginUiModelBuilder
  fun setDescription(description: String?): PluginUiModelBuilder
  fun setVendor(vendor: String?): PluginUiModelBuilder
  fun setProductCode(productCode: String?): PluginUiModelBuilder
  fun setCategory(category: String?): PluginUiModelBuilder
  fun setChangeNotes(changeNotes: String?): PluginUiModelBuilder
  fun setSinceBuild(sinceBuild: String?): PluginUiModelBuilder
  fun setUntilBuild(untilBuild: String?): PluginUiModelBuilder
  fun setDownloads(downloads: String?): PluginUiModelBuilder
  fun setRating(rating: String?): PluginUiModelBuilder
  fun setSize(size: String?): PluginUiModelBuilder
  fun setVendorEmail(vendorEmail: String?): PluginUiModelBuilder
  fun setVendorUrl(vendorUrl: String?): PluginUiModelBuilder
  fun setUrl(url: String?): PluginUiModelBuilder
  fun setDownloadUrl(downloadUrl: String?): PluginUiModelBuilder
  fun setDate(date: String): PluginUiModelBuilder
  fun setDependencies(dependencies: List<PluginDependencyModel>): PluginUiModelBuilder
  fun addDependency(id: String, optional: Boolean): PluginUiModelBuilder
  fun addTag(tag: String): PluginUiModelBuilder
  fun setIncomplete(incomplete: Boolean): PluginUiModelBuilder
  fun setIsConverted(converted: Boolean): PluginUiModelBuilder
  fun setIsPaid(isPaid: Boolean): PluginUiModelBuilder
  fun setIsFromMarketPlace(isFromMarketPlace: Boolean): PluginUiModelBuilder
  fun setExternalPluginId(externalPluginId: String?): PluginUiModelBuilder
  fun setExternalUpdateId(externalUpdateId: String?): PluginUiModelBuilder
  fun setTags(tags: List<String>?): PluginUiModelBuilder
  fun setDate(date: Long): PluginUiModelBuilder
  fun setRepositoryName(repositoryName: String): PluginUiModelBuilder
  fun setVendorDetails(organization: String?): PluginUiModelBuilder
  fun setDisableAllowed(disabledAllowed: Boolean): PluginUiModelBuilder

  fun build(): PluginUiModel
}

@ApiStatus.Internal
@IntellijInternalApi
interface PluginUiModelBuilderFactory {

  fun createBuilder(id: PluginId): PluginUiModelBuilder

  companion object {
    @JvmStatic
    fun getInstance(): PluginUiModelBuilderFactory {
      if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
        return PluginDtoModelBuilderFactory
      }
      else {
        return PluginNodeModelBuilderFactory
      }
    }
  }
}