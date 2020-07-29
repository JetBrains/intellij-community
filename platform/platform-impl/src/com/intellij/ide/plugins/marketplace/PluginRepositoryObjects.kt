// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.newui.Tags

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
  val version: String = "",
  val notes: String = "",
  val dependencies: Set<String> = emptySet(),
  val since: String? = null,
  val until: String? = null,
  val productCode: String? = null,
  val url: String? = null,
  val size: Int = 0
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode()
    pluginNode.setId(id)
    pluginNode.name = name
    pluginNode.description = description
    pluginNode.vendor = vendor
    pluginNode.tags = tags
    pluginNode.changeNotes = notes
    pluginNode.sinceBuild = since
    pluginNode.untilBuild = until
    pluginNode.productCode = productCode
    pluginNode.version = version
    pluginNode.url = url
    pluginNode.size = size.toString()
    for (dep in dependencies) pluginNode.addDepends(dep)
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
  val vendor: String = "",
  @get:JsonProperty("updateId")
  val externalUpdateId: String? = null,
  @get:JsonProperty("id")
  val externalPluginId: String? = null,
  val downloads: String = ""
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode()
    pluginNode.setId(id)
    pluginNode.name = name
    pluginNode.rating = String.format("%.2f", rating)
    pluginNode.downloads = downloads
    pluginNode.vendor = vendor
    pluginNode.externalPluginId = externalPluginId
    pluginNode.externalUpdateId = externalUpdateId
    if (isPaid) pluginNode.tags = listOf(Tags.Paid.name)
    return pluginNode
  }
}

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
  val bundled: Boolean = false
)