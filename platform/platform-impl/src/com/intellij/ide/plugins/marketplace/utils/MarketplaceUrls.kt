// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.URLUtil
import java.net.URL

internal object MarketplaceUrls {
  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(ApplicationInfoImpl.getShadowInstanceImpl().pluginCompatibleBuild)

  const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"
  const val JB_PLUGINS_XML_IDS_FILENAME = "jbPluginsXMLIds.json"

  private val pluginManagerUrl by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/')
  }

  val pluginManagerHost: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
    URL(pluginManagerUrl).host
  }

  private val downloadUrl by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ApplicationInfoImpl.getShadowInstance().pluginDownloadUrl.trimEnd('/')
  }

  fun getPluginMetaUrl(externalPluginId: String) = "$pluginManagerUrl/files/$externalPluginId/meta.json"
  fun getUpdateMetaUrl(externalPluginId: String, externalUpdateId: String) =
    "$pluginManagerUrl/files/$externalPluginId/$externalUpdateId/meta.json"

  fun getJBPluginsXmlIdsUrl() = "$pluginManagerUrl/files/$JB_PLUGINS_XML_IDS_FILENAME"

  fun getPluginsXmlIdsUrl() = "$pluginManagerUrl/files/$FULL_PLUGINS_XML_IDS_FILENAME"

  fun getBrokenPluginsJsonUrl() = "$pluginManagerUrl/files/brokenPlugins.json"

  fun getIdeExtensionsJsonUrl() = Urls.newFromEncoded(
    "$pluginManagerUrl/files/IDE/extensions.json"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  fun getFeatureImplUrl(param: Map<String, String>) = Urls.newFromEncoded(
    "$pluginManagerUrl/feature/getImplementations"
  ).addParameters(param)

  fun getSearchAggregationUrl(field: String) = Urls.newFromEncoded(
    "$pluginManagerUrl/api/search/aggregation/$field"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  fun getSearchCompatibleUpdatesUrl() = Urls.newFromEncoded("$pluginManagerUrl/api/search/compatibleUpdates").toExternalForm()

  fun getSearchPluginsUrl(query: String, count: Int, includeIncompatible: Boolean): Url {
    val params = mapOf(
      "build" to IDE_BUILD_FOR_REQUEST,
      "max" to count.toString(),
      "all" to includeIncompatible.toString()
    )
    return Urls.newFromEncoded(
      "$pluginManagerUrl/api/search/plugins?$query"
    ).addParameters(params)
  }

  fun getPluginReviewsUrl(pluginId: PluginId, page: Int): Url {
    val pageValue = if (page == 1) "" else "?page=$page"
    return Urls.newFromEncoded("$pluginManagerUrl/api/products/intellij/plugins/${pluginId.urlEncode()}/comments$pageValue")
  }

  @JvmStatic
  fun getPluginHomepage(pluginId: PluginId) = "$pluginManagerUrl/plugin/index?xmlId=${pluginId.urlEncode()}"

  @JvmStatic
  fun getPluginReviewNoteUrl() = "https://plugins.jetbrains.com/docs/marketplace/reviews-policy.html"

  @JvmStatic
  fun getPluginWriteReviewUrl(pluginId: PluginId, version: String? = null) = buildString {
    append("$pluginManagerUrl/intellij/${pluginId.urlEncode()}/review/new")
    append("?build=$IDE_BUILD_FOR_REQUEST")
    version?.let {
      append("&version=$it")
    }
  }

  @JvmStatic
  fun getPluginDownloadUrl(
    descriptor: IdeaPluginDescriptor,
    uuid: String,
    buildNumber: BuildNumber?,
    currentVersion: IdeaPluginDescriptor?
  ): String {
    val updatedFrom = currentVersion?.version ?: ""
    val parameters = hashMapOf(
      "id" to descriptor.pluginId.idString,
      "build" to ApplicationInfoImpl.orFromPluginCompatibleBuild(buildNumber),
      "uuid" to uuid,
      "updatedFrom" to updatedFrom
    )
    (descriptor as? PluginNode)?.channel?.let {
      parameters["channel"] = it
    }

    return Urls.newFromEncoded(downloadUrl)
      .addParameters(parameters)
      .toExternalForm()
  }

  private fun PluginId.urlEncode() = URLUtil.encodeURIComponent(idString)
}