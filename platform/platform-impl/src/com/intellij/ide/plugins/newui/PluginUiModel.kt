// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.getTags
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import java.text.DecimalFormat
import java.util.*

/**
 * A lightweight model for representing plugin information in the UI.
 * This interface contains only the subset of plugin metadata needed for display purposes.
 */
@ApiStatus.Internal
interface PluginUiModel {
  val pluginId: PluginId
  @get:NlsSafe
  val version: String?
  val isBundled: Boolean
  val isDeleted: Boolean

  val isIncompatibleWithCurrentOs: Boolean
  val isIncompatible: Boolean
  val canBeEnabled: Boolean
  val requiresUpgrade: Boolean
  val isFromMarketplace: Boolean
  val isLicenseOptional: Boolean
  val isConverted: Boolean
  val detailsLoaded: Boolean
  val allowBundledUpdate: Boolean

  val source: PluginSource
  val dependencies: List<PluginDependencyModel>
  val dependencyNames: Collection<String>?
  val suggestedFeatures: Collection<String>

  @get:NlsSafe
  val vendor: String?
  @get:NlsSafe
  val organization: String?
  @get:NlsSafe
  val changeNotes: String?
  @get:NlsSafe
  val productCode: String?
  @get:NlsSafe
  val size: String?
  @get:NlsSafe
  val downloadUrl: String?
  val reviewComments: PageContainer<PluginReviewComment>?

  @get:NlsSafe
  var forumUrl: String?
  @get:NlsSafe
  var licenseUrl: String?
  @get:NlsSafe
  var bugtrackerUrl: String?
  @get:NlsSafe
  var documentationUrl: String?
  @get:NlsSafe
  var sourceCodeUrl: String?
  @get:NlsSafe
  var reportPluginUrl: String?

  @get:NlsSafe
  var verifiedName: String?
  var isVerified: Boolean
  var isTrader: Boolean

  var screenShots: List<String>?
  var externalPluginIdForScreenShots: String? 
  var externalPluginId: String?
  var externalUpdateId: String?
  var defaultTrialPeriod: Int?
  var customTrialPeriods: Map<String, Int>?
  var date: Long


  @get:NlsSafe
  var name: String?
  var tags: List<String>?
  var suggestedCommercialIde: String?
  
  @get:NlsSafe
  var downloads: String?
  @get:NlsSafe
  var rating: String?
  @get:NlsSafe
  var repositoryName: String?
  var installSource: FUSEventSource?
  @get:NlsSafe
  var description: String?

  fun addDependency(id: PluginId, optional: Boolean)
  /**
   * Java compatibility method. Going to be removed after refactoring is done.
   */
  fun getDescriptor(): IdeaPluginDescriptor = this.getPluginDescriptor()

  companion object {
    @JvmStatic
    fun getDescriptorOrNull(model: PluginUiModel?): IdeaPluginDescriptor? = model?.getPluginDescriptor()
  }
}

@ApiStatus.Internal
fun PluginUiModel.getPluginDescriptor(): IdeaPluginDescriptor {
  if (this is PluginUiModelAdapter) return pluginDescriptor
  throw IllegalStateException("PluginUiModelAdapter expected")
}

@ApiStatus.Internal
enum class PluginSource {
  LOCAL, REMOTE
}

/**
 * Represents a plugin dependency in the UI model
 */
@ApiStatus.Internal
data class PluginDependencyModel(
  val pluginId: PluginId,
  val isOptional: Boolean,
)

private val K_FORMAT = DecimalFormat("###.#K")
private val M_FORMAT = DecimalFormat("###.#M")

@NlsSafe
@ApiStatus.Internal
fun PluginUiModel.presentableRating(): String? {
  val rating = this.rating ?: return null
  if (rating.isBlank()) return null

  try {
    val value = rating.toDouble()
    if (value > 0) return rating.removeSuffix(".0")
  }
  catch (_: NumberFormatException) { }
  return null
}

@NlsSafe
@ApiStatus.Internal
fun PluginUiModel.presentableDownloads(): String? {
  val downloads = this.downloads ?: return null
  if (downloads.isBlank()) return null

  try {
    val value = downloads.toLong()
    return when {
      value <= 1000 -> value.toString()
      value < 1000000 -> K_FORMAT.format(value / 1000.0)
      else -> M_FORMAT.format(value / 1000000.0)
    }
  }
  catch (_: NumberFormatException) { }
  return null
}

@NlsSafe
@ApiStatus.Internal
fun PluginUiModel.presentableSize(): String? {
  val size = this.size ?: return null
  if (size.isBlank()) return null

  try {
    val value = size.toLong()
    return if (value >= 0) StringUtil.formatFileSize(value).uppercase(Locale.ENGLISH) else null
  }
  catch (_: NumberFormatException) { }
  return null
}

@ApiStatus.Internal
fun PluginUiModel.calculateTags(): List<String> {
  return this.getDescriptor().getTags()
}

@NlsSafe
@ApiStatus.Internal
fun PluginUiModel.presentableDate(): String? {
  return if (date > 0 && date != Long.MAX_VALUE) {
    PluginManagerConfigurable.DATE_FORMAT.format(Date(date))
  }
  else {
    null
  }
}

@ApiStatus.Internal
fun PluginUiModel.getTrialPeriodByProductCode(code: String): Int? {
  return customTrialPeriods?.getOrDefault(code, defaultTrialPeriod!!)
}