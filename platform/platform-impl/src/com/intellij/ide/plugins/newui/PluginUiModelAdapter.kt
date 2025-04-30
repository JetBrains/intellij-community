// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.getUnfulfilledOsRequirement
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.util.Date

/**
 * A temporary class used to eliminate "runtime" PluginDescriptor usages in the UI. It will later be replaced with frontend and backend implementations.
 */
@ApiStatus.Internal
class PluginUiModelAdapter(
  val pluginDescriptor: IdeaPluginDescriptor,
) : PluginUiModel {
  override val pluginId: PluginId = pluginDescriptor.pluginId
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
  override val isIncompatible: Boolean
    get() = PluginManagerCore.isIncompatible(pluginDescriptor)
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
  override val isPaid: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.getIsPaid() else false

  override val source: PluginSource = PluginSource.LOCAL
  override val dependencies: List<PluginDependencyModel>
    get() = pluginDescriptor.dependencies.map { PluginDependencyModel(it.pluginId, it.isOptional) }
  override val dependencyNames: Collection<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.dependencyNames else null
  override val suggestedFeatures: Collection<String>
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedFeatures else emptyList()

  override val vendor: String?
    get() = pluginDescriptor.vendor
  override val organization: String?
    get() = pluginDescriptor.organization
  override val changeNotes: String?
    get() = pluginDescriptor.changeNotes
  override val productCode: String?
    get() = pluginDescriptor.productCode
  override val size: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.size else null
  override val downloadUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.downloadUrl else null
  override val releaseDate: Date?
    get() = pluginDescriptor.releaseDate
  override val releaseVersion: Int
    get() = pluginDescriptor.releaseVersion
  override val reviewComments: PageContainer<PluginReviewComment>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reviewComments else null

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
  override var externalPluginIdForScreenShots: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.externalPluginIdForScreenShots else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.externalPluginIdForScreenShots = value
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

  override var name: String?
    get() = pluginDescriptor.name
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.name = value
      }
    }

  override var tags: List<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.tags else emptyList()
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.tags = value
      }
    }

  override var installSource: FUSEventSource?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.installSource else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.installSource = value
      }
    }

  override fun addDependency(id: PluginId, optional: Boolean) {
    if (pluginDescriptor is PluginNode) {
      pluginDescriptor.addDepends(pluginId, optional)
    }
  }

  override var suggestedCommercialIde: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedCommercialIde else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.suggestedCommercialIde = value
      }
    }
  override var downloads: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.downloads else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.downloads = value
      }
    }
  override var rating: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.rating else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.rating = value
      }
    }
  override var repositoryName: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.repositoryName else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.repositoryName = value
      }
    }
  override var channel: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.channel else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.channel = value
      }
    }
  override var date: Long
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.date else Long.MAX_VALUE
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.date = value
      }
    }
  override var isEnabled: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isEnabled else true
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.isEnabled = value
      }
    }
  override var description: String?
    get() = pluginDescriptor.description
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.description = value
      }
    }

  override fun getDescriptor(): IdeaPluginDescriptor = pluginDescriptor

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PluginUiModelAdapter

    if (pluginDescriptor != other.pluginDescriptor) return false
    if (source != other.source) return false

    return true
  }

  override fun hashCode(): Int {
    return pluginDescriptor.hashCode()
  }
}