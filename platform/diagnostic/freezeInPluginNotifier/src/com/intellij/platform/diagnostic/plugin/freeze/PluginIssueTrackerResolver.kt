// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
private data class MarketplacePluginInfo(
  val urls: PluginUrls,
  val icon: String,
)

@Serializable
private data class PluginUrls(
  val url: String,
  val bugtrackerUrl: String,
)

internal object PluginIssueTrackerResolver {
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun getMarketplaceBugTrackerUrl(pluginDescriptor: PluginDescriptor): String? {
    val url = pluginDescriptor.url?.replace("/plugin/", "/api/plugins/")
    if (url == null) return MarketplaceUrls.getPluginHomepage(pluginDescriptor.pluginId)

    val timeoutMillis = TimeUnit.SECONDS.toMillis(5)
    return withContext(Dispatchers.IO) {
      runCatching {
        val response = HttpRequests.request(url).connectTimeout(timeoutMillis.toInt()).readTimeout(timeoutMillis.toInt()).readString()
        val pluginInfo: MarketplacePluginInfo = json.decodeFromString(response)
        pluginInfo.urls.bugtrackerUrl
      }.getOrDefault(MarketplaceUrls.getPluginHomepage(pluginDescriptor.pluginId))
    }
  }
}