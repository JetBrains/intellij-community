// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.PluginNodeVendorDetails
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelBuilderFactory
import com.intellij.ide.plugins.newui.Tags
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.parseLong
import com.intellij.openapi.util.text.StringUtil.unquoteString
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*

/**
 * Object from Search Service for getting compatible updates for IDE.
 * [externalPluginId] plugin ID from Plugin Repository database.
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class IdeCompatibleUpdate(
  @get:JsonProperty("id")
  val externalUpdateId: String = "",
  @get:JsonProperty("pluginId")
  val externalPluginId: String = "",
  @get:JsonProperty("pluginXmlId")
  val pluginId: String = "",
  val version: String = "",
)

/**
 * Plugin Repository object for storing information about plugin updates.
 */
@Serializable
@ApiStatus.Internal
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
  val size: Int = 0,
) {
  fun toUiModel(): PluginUiModel {
    val pluginId = PluginId.getId(id)
    val builder = PluginUiModelBuilderFactory.getInstance().createBuilder(pluginId)

    builder.setName(name)
    builder.setSize(size.toString())

    builder.setDescription(description)
    builder.setVendor(vendor)
    builder.setTags(tags)
    builder.setChangeNotes(notes)
    builder.setSinceBuild(since)
    builder.setUntilBuild(until)
    builder.setProductCode(productCode)
    builder.setVersion(version)
    builder.setVendorDetails(organization)
    builder.setUrl(url)
    builder.setIsFromMarketPlace(true)

    for (dep in dependencies) {
      builder.addDependency(dep, false)
    }
    for (dep in optionalDependencies) {
      builder.addDependency(dep, true)
    }

    val model = builder.build()

    RepositoryHelper.addMarketplacePluginDependencyIfRequired(model)
    return model
  }
}

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
class MarketplaceSearchPluginData(
  @get:JsonProperty("xmlId")
  val id: String = "",
  var isPaid: Boolean = false,
  val rating: Double = 0.0,
  val name: String = "",
  val cdate: Long? = null,
  val organization: String = "",
  @get:JsonProperty("updateId")
  val externalUpdateId: String? = null,
  @get:JsonProperty("id")
  val externalPluginId: String? = null,
  val downloads: String = "",
  @get:JsonProperty("nearestUpdate")
  val nearestUpdate: NearestUpdate? = null,
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode(PluginId.getId(id))
    pluginNode.name = name
    pluginNode.rating = "%.2f".format(Locale.US, rating)
    pluginNode.downloads = downloads
    pluginNode.setVendorDetails(organization)
    pluginNode.externalPluginId = externalPluginId
    pluginNode.externalUpdateId = externalUpdateId ?: nearestUpdate?.id
    pluginNode.isPaid = isPaid
    if (cdate != null) pluginNode.date = cdate
    if (isPaid) pluginNode.tags = listOf(Tags.Paid.name)
    return pluginNode
  }

  fun toPluginUiModel(): PluginUiModel {
    val pluginId = PluginId.getId(id)
    val builder = PluginUiModelBuilderFactory.getInstance().createBuilder(pluginId)

    builder.setName(name)
    builder.setRating("%.2f".format(Locale.US, rating))
    builder.setDownloads(downloads)
    builder.setVendorDetails(organization)
    builder.setExternalPluginId(externalPluginId)
    builder.setExternalUpdateId(externalUpdateId ?: nearestUpdate?.id)
    builder.setIsPaid(isPaid)
    builder.setIsFromMarketPlace(true)

    if (cdate != null) {
      builder.setDate(cdate)
    }
    if (isPaid) {
      builder.setTags(listOf(Tags.Paid.name))
    }
    return builder.build()
  }
}

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
class NearestUpdate(
  @get:JsonProperty("id")
  val id: String? = null,
  @get:JsonProperty("xmlId")
  val pluginId: String = "",
  @get:JsonProperty("products")
  val products: List<String> = emptyList(),
  @get:JsonProperty("updateCompatibility")
  val updateCompatibility: Map<String, Long> = emptyMap(),
  @get:JsonProperty("isCompatible")
  val compatible: Boolean = true,
)

/**
 * @param aggregations map of results and count of plugins
 * @param total count of plugins
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal class AggregationSearchResponse(val aggregations: Map<String, Int> = emptyMap(), val total: Int = 0)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FeatureImpl(
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
internal class MarketplaceBrokenPlugin(
  val id: String = "",
  val version: String = "",
  val since: String? = null,
  val until: String? = null,
  val originalSince: String? = null,
  val originalUntil: String? = null,
)

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginReviewComment(
  val id: String = "",
  val cdate: String = "",
  val comment: @Nls String = "",
  val rating: Int = 0,
  val author: ReviewCommentAuthor = ReviewCommentAuthor(),
  val plugin: ReviewCommentPlugin = ReviewCommentPlugin(),
) {
  fun getDate(): Long = parseLong(cdate, 0)
}

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class ReviewCommentAuthor(
  val name: @Nls String = "",
)

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class ReviewCommentPlugin(
  val link: @Nls String = "",
)

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class SalesMetadata(
  val trialPeriod: Int? = null,
  val customTrialPeriods: List<CustomTrialPeriod>? = null,
)

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomTrialPeriod(
  @JsonProperty("productCode") val productCode: String,
  @JsonProperty("trialPeriod") val trialPeriod: Int,
)

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class IntellijPluginMetadata(
  val screenshots: List<String>? = null,
  val vendor: PluginVendorMetadata? = null,
  val forumUrl: String? = null,
  val licenseUrl: String? = null,
  val bugtrackerUrl: String? = null,
  val documentationUrl: String? = null,
  val sourceCodeUrl: String? = null,
  val reportPluginUrl: String? = null,
  val salesInfo: SalesMetadata? = null,
) {

  fun toPluginNode(pluginNode: PluginNode) {
    if (vendor != null) {
      pluginNode.setVendorDetails(
        vendor.name,
        vendor.url,
        vendor.trader,
        vendor.verified
      )
    }
    pluginNode.forumUrl = forumUrl
    pluginNode.licenseUrl = licenseUrl
    pluginNode.bugtrackerUrl = bugtrackerUrl
    pluginNode.documentationUrl = documentationUrl
    pluginNode.sourceCodeUrl = sourceCodeUrl
    pluginNode.reportPluginUrl = reportPluginUrl
    pluginNode.defaultTrialPeriod = salesInfo?.trialPeriod
    pluginNode.setCustomTrialPeriodMap(salesInfo?.customTrialPeriods?.associate { p ->
      p.productCode to p.trialPeriod
    })
  }


  @ApiStatus.Internal
  fun toPluginUiModel(model: PluginUiModel) {
    if (vendor != null) {
      val details = PluginNodeVendorDetails(vendor.name, vendor.url, vendor.trader, vendor.verified)
      model.vendorDetails = details
    }

    model.forumUrl = forumUrl
    model.licenseUrl = licenseUrl
    model.bugtrackerUrl = bugtrackerUrl
    model.documentationUrl = documentationUrl
    model.sourceCodeUrl = sourceCodeUrl
    model.reportPluginUrl = reportPluginUrl

    screenshots?.let { model.screenShots = it }

    model.defaultTrialPeriod = salesInfo?.trialPeriod
    model.customTrialPeriods = salesInfo?.customTrialPeriods?.associate { p ->
      p.productCode to p.trialPeriod
    }
  }
}

@Serializable
@ApiStatus.Internal
@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginVendorMetadata(
  val name: String = "",
  val url: String? = null,
  @get:JsonProperty("isTrader")
  val trader: Boolean = false,
  @get:JsonProperty("isVerified")
  val verified: Boolean = false,
)