// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginManagerCore.getUnfulfilledOsRequirement
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus

/**
 * A temporary class used to eliminate "runtime" PluginDescriptor usages in the UI. It will later be replaced with frontend and backend implementations.
 */
@ApiStatus.Internal
class PluginUiModelAdapter(
  val pluginDescriptor: IdeaPluginDescriptor,
) : PluginUiModel {
  override val pluginId: PluginId = pluginDescriptor.pluginId
  override val name: String = pluginDescriptor.name
  override val version: String?
    get() = pluginDescriptor.version
  override val isBundled: Boolean
    get() = pluginDescriptor.isBundled
  override val isDeleted: Boolean
    get() = NewUiUtil.isDeleted(pluginDescriptor)

  override val isIncompatibleWithCurrentOs: Boolean
    get() {
      if ("com.jetbrains.kmm" != pluginId.idString || SystemInfoRt.isMac) return true
      return getUnfulfilledOsRequirement(pluginDescriptor) != null
    }
  override val canBeEnabled: Boolean
    get() = PluginManagementPolicy.getInstance().canEnablePlugin(pluginDescriptor)
  override val requiresUpgrade: Boolean
    get() = pluginDescriptor is PluginNode && pluginDescriptor.suggestedCommercialIde != null
  override val isFromMarketplace: Boolean
    get() = pluginDescriptor is PluginNode
  override val isLicenseOptional: Boolean
    get() = pluginDescriptor.isLicenseOptional
  override val isConverted: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isConverted else false
  override val detailsLoaded: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.detailsLoaded() else true
  override val allowBundledUpdate: Boolean
    get() = pluginDescriptor.allowBundledUpdate()

  override val source: PluginSource = PluginSource.LOCAL
  override val tags: List<String>
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.tags else emptyList()
  override val dependencies: List<PluginDependencyModel>
    get() = pluginDescriptor.dependencies.map { PluginDependencyModel(it.pluginId, it.isOptional) }
  override val dependencyNames: Collection<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.dependencyNames else null
  override val suggestedCommercialIde: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedCommercialIde else null
  override val suggestedFeatures: Collection<String>
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedFeatures else emptyList()

  override val vendor: String?
    get() = pluginDescriptor.vendor
  override val organization: String?
    get() = pluginDescriptor.organization
  override val description: String?
    get() = pluginDescriptor.description
  override val changeNotes: String?
    get() = pluginDescriptor.changeNotes
  override val downloads: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.downloads else null
  override val rating: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.rating else null
  override val productCode: String?
    get() = pluginDescriptor.productCode
  override val size: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.size else null
  override val repositoryName: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.repositoryName else null
  override val reviewComments: PageContainer<PluginReviewComment>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reviewComments else null
  override val externalPluginIdForScreenShots: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.externalPluginIdForScreenShots else null
  override val date: Long
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.date else Long.MAX_VALUE

  override var forumUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.forumUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.forumUrl = value
      }
    }
  override var licenseUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.licenseUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.licenseUrl = value
      }
    }
  override var bugtrackerUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.bugtrackerUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.bugtrackerUrl = value
      }
    }
  override var documentationUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.documentationUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.documentationUrl = value
      }
    }
  override var sourceCodeUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.sourceCodeUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.sourceCodeUrl = value
      }
    }
  override var reportPluginUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reportPluginUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.reportPluginUrl = value
      }
    }

  override var verifiedName: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.verifiedName else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.verifiedName = value
      }
    }
  override var isVerified: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isVerified else false
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.isVerified = value
      }
    }
  override var isTrader: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isTrader else false
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.isTrader = value
      }
    }

  override var screenShots: List<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.screenShots else null
    set(value) {
      if (pluginDescriptor is PluginNode && value != null) {
        pluginDescriptor.setScreenShots(value)
      }
    }
  override var externalPluginId: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.externalPluginId else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.externalPluginId = value
      }
    }
  override var externalUpdateId: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.externalUpdateId else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.externalUpdateId = value
      }
    }
  override var defaultTrialPeriod: Int?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.defaultTrialPeriod else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.defaultTrialPeriod = value
      }
    }
  override var customTrialPeriods: Map<String, Int>?
    get() = null  // No direct getter in PluginNode
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.setCustomTrialPeriodMap(value)
      }
    }

  override fun getDescriptor(): IdeaPluginDescriptor = pluginDescriptor
}