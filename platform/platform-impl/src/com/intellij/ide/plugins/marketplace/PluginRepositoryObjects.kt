// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.newui.Tags
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.parseLong
import com.intellij.openapi.util.text.StringUtil.unquoteString
import org.jetbrains.annotations.Nls
import java.util.*

/**
 * Object from Search Service for getting compatible updates for IDE.
 * [externalUpdateId] update ID from Plugin Repository database.
 * [externalPluginId] plugin ID from Plugin Repository database.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IdeCompatibleUpdate(
  @get:JsonProperty("id")
  val externalUpdateId: String = "",
  @get:JsonProperty("pluginId")
  val externalPluginId: String = "",
  @get:JsonProperty("pluginXmlId")
  val pluginId: String = "",
  val version: String = ""
)

/**
 * Plugin Repository object for storing information about plugin updates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IntellijUpdateMetadata(
  @get:JsonProperty("xmlId")
  val id: String = "",
  val name: String = "",
  val description: String = "",
  val tags: List<String> = emptyList(),
  val vendor: String = "",
  val organization: String = "",
  val version: String = "",
  val notes: String = "",
  val dependencies: Set<String> = emptySet(),
  val optionalDependencies: Set<String> = emptySet(),
  val since: String? = null,
  val until: String? = null,
  val productCode: String? = null,
  val url: String? = null,
  val size: Int = 0
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode(PluginId.getId(id), name, size.toString())
    pluginNode.description = description
    pluginNode.vendor = vendor
    pluginNode.tags = tags
    pluginNode.changeNotes = notes
    pluginNode.sinceBuild = since
    pluginNode.untilBuild = until
    pluginNode.productCode = productCode
    pluginNode.version = version
    pluginNode.organization = organization
    pluginNode.url = url
    for (dep in dependencies) {
      pluginNode.addDepends(dep, false)
    }
    for (dep in optionalDependencies) {
      pluginNode.addDepends(dep, true)
    }

    RepositoryHelper.addMarketplacePluginDependencyIfRequired(pluginNode)

    return pluginNode
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal class MarketplaceSearchPluginData(
  @get:JsonProperty("xmlId")
  val id: String = "",
  var isPaid: Boolean = false,
  val rating: Double = 0.0,
  val name: String = "",
  private val cdate: Long? = null,
  val organization: String = "",
  @get:JsonProperty("updateId")
  val externalUpdateId: String? = null,
  @get:JsonProperty("id")
  val externalPluginId: String? = null,
  val downloads: String = "",
  @get:JsonProperty("nearestUpdate")
  val nearestUpdate: NearestUpdate? = null
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode(PluginId.getId(id))
    pluginNode.name = name
    pluginNode.rating = "%.2f".format(Locale.US, rating)
    pluginNode.downloads = downloads
    pluginNode.organization = organization
    pluginNode.externalPluginId = externalPluginId
    pluginNode.externalUpdateId = externalUpdateId ?: nearestUpdate?.id
    pluginNode.isPaid = isPaid
    if (cdate != null) pluginNode.date = cdate
    if (isPaid) pluginNode.tags = listOf(Tags.Paid.name)
    return pluginNode
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal class NearestUpdate(
  @get:JsonProperty("id")
  val id: String? = null,
  @get:JsonProperty("products")
  val products: List<String> = emptyList(),
  @get:JsonProperty("isCompatible")
  val compatible: Boolean = true
)

/**
 * @param aggregations map of results and count of plugins
 * @param total count of plugins
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal class AggregationSearchResponse(val aggregations: Map<String, Int> = emptyMap(), val total: Int = 0)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeatureImpl(
  val pluginId: String? = null,
  val pluginName: String? = null,
  val description: String? = null,
  val version: String? = null,
  val implementationName: String? = null,
  val bundled: Boolean = false,
) {

  fun toPluginData(isFromCustomRepository: Boolean = false): PluginData? {
    return pluginId
      ?.let { unquoteString(it) }
      ?.let { id ->
        PluginData(
          id,
          pluginName?.let { unquoteString(it) },
          bundled,
          isFromCustomRepository,
        )
      }
  }

  fun toPluginData(isFromCustomRepository: (String) -> Boolean): PluginData? {
    return pluginId
      ?.let { toPluginData(isFromCustomRepository.invoke(it)) }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class MarketplaceBrokenPlugin(
  val id: String = "",
  val version: String = "",
  val since: String? = null,
  val until: String? = null,
  val originalSince: String? = null,
  val originalUntil: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginReviewComment(
  val id: String = "",
  val cdate: String = "",
  val comment: @Nls String = "",
  val rating: Int = 0,
  val author: ReviewCommentAuthor = ReviewCommentAuthor(),
  val plugin: ReviewCommentPlugin = ReviewCommentPlugin()
) {
  fun getDate(): Long = parseLong(cdate, 0)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReviewCommentAuthor(
  val name: @Nls String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReviewCommentPlugin(
  val link: @Nls String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IntellijPluginMetadata(
  val screenshots: List<String>? = null,
  val vendor: PluginVendorMetadata? = null,
  val forumUrl: String? = null,
  val licenseUrl: String? = null,
  val bugtrackerUrl: String? = null,
  val documentationUrl: String? = null,
  val sourceCodeUrl: String? = null) {

  fun toPluginNode(pluginNode: PluginNode) {
    if (vendor != null) {
      pluginNode.verifiedName = vendor.name
      pluginNode.isVerified = vendor.verified
      pluginNode.isTrader = vendor.trader
    }
    pluginNode.forumUrl = forumUrl
    pluginNode.licenseUrl = licenseUrl
    pluginNode.bugtrackerUrl = bugtrackerUrl
    pluginNode.documentationUrl = documentationUrl
    pluginNode.sourceCodeUrl = sourceCodeUrl
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginVendorMetadata(
  val name: String = "",
  @get:JsonProperty("isTrader")
  val trader: Boolean = false,
  @get:JsonProperty("isVerified")
  val verified: Boolean = false
)