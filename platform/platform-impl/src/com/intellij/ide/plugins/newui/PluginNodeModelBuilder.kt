// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
class PluginNodeModelBuilder(private val pluginId: PluginId) : PluginUiModelBuilder {
  private val pluginNode = PluginNode(pluginId)
  
  override fun setId(id: String): PluginUiModelBuilder {
    pluginNode.setId(id)
    return this
  }

  override fun setName(name: String?): PluginUiModelBuilder {
    pluginNode.name = name
    return this
  }
  
  override fun setVersion(version: String?): PluginUiModelBuilder {
    if (version != null) pluginNode.version = version
    return this
  }
  
  override fun setDescription(description: String?): PluginUiModelBuilder {
    pluginNode.description = description
    return this
  }
  
  override fun setVendor(vendor: String?): PluginUiModelBuilder {
    pluginNode.vendor = vendor
    return this
  }
  
  override fun setProductCode(productCode: String?): PluginUiModelBuilder {
    pluginNode.productCode = productCode
    return this
  }
  
  override fun setCategory(category: String?): PluginUiModelBuilder {
    if (category != null) pluginNode.setCategory(category)
    return this
  }
  
  override fun setChangeNotes(changeNotes: String?): PluginUiModelBuilder {
    pluginNode.changeNotes = changeNotes
    return this
  }
  
  override fun setSinceBuild(sinceBuild: String?): PluginUiModelBuilder {
    pluginNode.sinceBuild = sinceBuild
    return this
  }
  
  override fun setUntilBuild(untilBuild: String?): PluginUiModelBuilder {
    pluginNode.untilBuild = untilBuild
    return this
  }
  
  override fun setDownloads(downloads: String?): PluginUiModelBuilder {
    pluginNode.downloads = downloads
    return this
  }
  
  override fun setRating(rating: String?): PluginUiModelBuilder {
    pluginNode.rating = rating
    return this
  }
  
  override fun setDate(date: Long): PluginUiModelBuilder {
    pluginNode.date = date
    return this
  }

  override fun setRepositoryName(repositoryName: String): PluginUiModelBuilder {
    pluginNode.repositoryName = repositoryName
    return this
  }

  override fun setVendorDetails(organization: String?): PluginUiModelBuilder {
    pluginNode.setVendorDetails(organization)
    return this
  }

  override fun setDisableAllowed(disabledAllowed: Boolean): PluginUiModelBuilder {
    return this
  }

  override fun setSize(size: String?): PluginUiModelBuilder {
    if (size != null) pluginNode.size = size
    return this
  }
  
  override fun setVendorEmail(vendorEmail: String?): PluginUiModelBuilder {
    pluginNode.vendorEmail = vendorEmail
    return this
  }
  
  override fun setVendorUrl(vendorUrl: String?): PluginUiModelBuilder {
    pluginNode.vendorUrl = vendorUrl
    return this
  }
  
  override fun setUrl(url: String?): PluginUiModelBuilder {
    pluginNode.url = url
    return this
  }
  
  override fun setDownloadUrl(downloadUrl: String?): PluginUiModelBuilder {
    if (downloadUrl != null) pluginNode.downloadUrl = downloadUrl
    return this
  }
  
  override fun setDate(date: String): PluginUiModelBuilder {
    pluginNode.setDate(date)
    return this
  }

  override fun setDependencies(dependencies: List<PluginDependencyModel>): PluginUiModelBuilder {
    pluginNode.dependencies.clear()
    dependencies.forEach { addDependency(it.pluginId.idString, it.isOptional) }
    return this
  }

  override fun addDependency(id: String, optional: Boolean): PluginUiModelBuilder {
    pluginNode.addDepends(id, optional)
    return this
  }
  
  override fun addTag(tag: String): PluginUiModelBuilder {
    pluginNode.addTags(tag)
    return this
  }
  
  override fun setIncomplete(incomplete: Boolean): PluginUiModelBuilder {
    pluginNode.setIncomplete(incomplete)
    return this
  }

  override fun setIsConverted(converted: Boolean): PluginUiModelBuilder {
    pluginNode.isConverted = converted
    return this
  }

  override fun setIsPaid(isPaid: Boolean): PluginUiModelBuilder {
    pluginNode.setIsPaid(isPaid)
    return this
  }

  override fun setIsFromMarketPlace(isFromMarketPlace: Boolean): PluginUiModelBuilder {
    return this
  }

  override fun setExternalPluginId(externalPluginId: String?): PluginUiModelBuilder {
    pluginNode.externalPluginId = externalPluginId
    return this
  }
  
  override fun setExternalUpdateId(externalUpdateId: String?): PluginUiModelBuilder {
    pluginNode.externalUpdateId = externalUpdateId
    return this
  }
  
  override fun setTags(tags: List<String>?): PluginUiModelBuilder {
    pluginNode.tags = tags
    return this
  }

  override fun build(): PluginUiModel {
    return PluginUiModelAdapter(pluginNode)
  }
}

@IntellijInternalApi
internal object PluginNodeModelBuilderFactory : PluginUiModelBuilderFactory {
  override fun createBuilder(id: PluginId): PluginUiModelBuilder = PluginNodeModelBuilder(id)
}