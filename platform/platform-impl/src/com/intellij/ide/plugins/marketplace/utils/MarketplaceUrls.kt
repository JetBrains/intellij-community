// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import java.net.URL

@ApiStatus.Internal
@IntellijInternalApi
object MarketplaceUrls {
  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(ApplicationInfoImpl.getShadowInstanceImpl().pluginCompatibleBuild)

  const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"
  const val JB_PLUGINS_XML_IDS_FILENAME = "jbPluginsXMLIds.json"
  const val EXTENSIONS_BACKUP_FILENAME = "pluginsFeatures.json"

  @JvmStatic
  fun getPluginManagerUrl() = MarketplaceCustomizationService.getInstance().getPluginManagerUrl().trimEnd('/')

  @JvmStatic
  fun getPluginManagerHost() = URL(getPluginManagerUrl()).host!!

  private fun getDownloadUrl() = MarketplaceCustomizationService.getInstance().getPluginDownloadUrl().trimEnd('/')

  fun getPluginMetaUrl(externalPluginId: String) = "${getPluginManagerUrl()}/files/$externalPluginId/meta.json"
  fun getUpdateMetaUrl(externalPluginId: String, externalUpdateId: String) =
    "${getPluginManagerUrl()}/files/$externalPluginId/$externalUpdateId/meta.json"

  fun getJBPluginsXmlIdsUrl() = "${getPluginManagerUrl()}/files/$JB_PLUGINS_XML_IDS_FILENAME"

  fun getPluginsXmlIdsUrl() = "${getPluginManagerUrl()}/files/$FULL_PLUGINS_XML_IDS_FILENAME"

  fun getBrokenPluginsJsonUrl() = "${getPluginManagerUrl()}/files/brokenPlugins.json"

  fun getIdeExtensionsJsonUrl() = Urls.newFromEncoded(
    "${getPluginManagerUrl()}/files/IDE/extensions.json"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  fun getFeatureImplUrl(param: Map<String, String>) = Urls.newFromEncoded(
    "${getPluginManagerUrl()}/feature/getImplementations"
  ).addParameters(param)

  fun getSearchAggregationUrl(field: String) = Urls.newFromEncoded(
    "${getPluginManagerUrl()}/api/search/aggregation/$field"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  @Deprecated("Use getSearchPluginsUpdatesUrl() instead")
  fun getSearchCompatibleUpdatesUrl() = Urls.newFromEncoded("${getPluginManagerUrl()}/api/search/compatibleUpdates").toExternalForm()

  fun getSearchPluginsUpdatesUrl() = Urls.newFromEncoded("${getPluginManagerUrl()}/api/search/updates/compatible").toExternalForm()

  fun getSearchNearestUpdate() = Urls.newFromEncoded("${getPluginManagerUrl()}/api/search/updates/nearest").toExternalForm()

  fun getSearchPluginsUrl(query: String, count: Int, includeIncompatible: Boolean): Url {
    val params = mapOf(
      "build" to IDE_BUILD_FOR_REQUEST,
      "max" to count.toString(),
      "all" to includeIncompatible.toString()
    )
    return Urls.newFromEncoded(
      "${getPluginManagerUrl()}/api/search/plugins?$query"
    ).addParameters(params)
  }

  fun getPluginReviewsUrl(pluginId: PluginId, page: Int): Url {
    val pageValue = if (page == 1) "" else "?page=$page"
    return Urls.newFromEncoded("${getPluginManagerUrl()}/api/products/intellij/plugins/${pluginId.urlEncode()}/comments$pageValue")
  }

  @JvmStatic
  fun getPluginHomepage(pluginId: PluginId): String? = MarketplaceCustomizationService.getInstance().getPluginHomepageUrl(pluginId)

  @JvmStatic
  fun getPluginReviewNoteUrl() = "${getPluginManagerUrl()}/docs/marketplace/reviews-policy.html" // plugin manager url?

  @JvmStatic
  fun getPluginWriteReviewUrl(pluginId: PluginId, version: String? = null): String = buildString {
    append("${getPluginManagerUrl()}/intellij/${pluginId.urlEncode()}/review/new")
    append("?build=$IDE_BUILD_FOR_REQUEST")
    version?.let {
      append("&version=$it")
    }
  }

  @JvmStatic
  fun getPluginDownloadUrl(
    descriptor: PluginUiModel,
    uuid: String,
    buildNumber: BuildNumber?,
    currentVersion: IdeaPluginDescriptor?,
  ): String {
    val updatedFrom = currentVersion?.version ?: ""
    val parameters = hashMapOf(
      "id" to descriptor.pluginId.idString,
      "build" to ApplicationInfoImpl.orFromPluginCompatibleBuild(buildNumber),
      "uuid" to uuid,
      "updatedFrom" to updatedFrom
    )
   descriptor.channel?.let {
      parameters["channel"] = it
    }

    return Urls.newFromEncoded(getDownloadUrl())
      .addParameters(parameters)
      .toExternalForm()
  }

  private fun PluginId.urlEncode() = URLUtil.encodeURIComponent(idString)
}