// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.readOrUpdateFile
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.setHeadersViaTuner
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.nio.file.Paths
import kotlin.jvm.Throws

@ApiStatus.Internal
object DefaultUiPluginManagerController : UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return PluginManagerCore.plugins.map { PluginUiModelAdapter(it) }
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return PluginManager.getVisiblePlugins(showImplementationDetails).map { PluginUiModelAdapter(it) }.toList()
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return InstalledPluginsState.getInstance().installedPlugins.map { PluginUiModelAdapter(it) }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isDisabled(pluginId)
  }

  override fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): List<MarketplaceSearchPluginData> {
    return HttpRequests
      .request(MarketplaceUrls.getSearchPluginsUrl(query, count, includeIncompatible))
      .setHeadersViaTuner()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(
          it.inputStream,
          object : TypeReference<List<MarketplaceSearchPluginData>>() {}
        )
      }
  }

  override fun loadUpdateMetadata(
    xmlId: String,
    ideCompatibleUpdate: IdeCompatibleUpdate,
    indicator: ProgressIndicator?
  ): IntellijUpdateMetadata {
    val updateMetadataFile = Paths.get(PathManager.getPluginTempPath(), "meta")
    return readOrUpdateFile(
      updateMetadataFile.resolve(ideCompatibleUpdate.externalUpdateId + ".json"),
      MarketplaceUrls.getUpdateMetaUrl(ideCompatibleUpdate.externalPluginId, ideCompatibleUpdate.externalUpdateId),
      indicator,
      IdeBundle.message("progress.downloading.plugins.meta", xmlId)
    ) {
      objectMapper.readValue(it, IntellijUpdateMetadata::class.java)
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @Throws(IOException::class)
  override fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return HttpRequests
      .request(MarketplaceUrls.getPluginReviewsUrl(pluginId, page))
      .setHeadersViaTuner()
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(it.inputStream, object : TypeReference<List<PluginReviewComment>>() {})
      }
  }
}

private val objectMapper: ObjectMapper by lazy { ObjectMapper() }