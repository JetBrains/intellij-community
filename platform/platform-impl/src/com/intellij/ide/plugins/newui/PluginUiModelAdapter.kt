// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.PluginManagerCore.getUnfulfilledOsRequirement
import com.intellij.ide.plugins.api.ReviewsPageContainer
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import org.jetbrains.annotations.ApiStatus

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
  override val isIncompatibleWithCurrentOs: Boolean
    get() {
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
  override val isEnabled: Boolean
    get() = !PluginManagerCore.isDisabled(pluginDescriptor.pluginId)

  override val dependencies: List<PluginDependencyModel>
    get() = pluginDescriptor.dependencies.map { PluginDependencyModel(it.pluginId, it.isOptional) }
  override val vendor: String?
    get() = pluginDescriptor.vendor
  override val organization: String?
    get() = pluginDescriptor.organization
  override val changeNotes: String?
    get() = pluginDescriptor.changeNotes

  override val productCode: String?
    get() = pluginDescriptor.productCode
  override val releaseDate: Long?
    get() = pluginDescriptor.releaseDate?.toInstant()?.toEpochMilli()
  override val size: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.size else null
  override val releaseVersion: Int
    get() = pluginDescriptor.releaseVersion
  override val displayCategory: String?
    get() = pluginDescriptor.displayCategory
  override val isImplementationDetail: Boolean
    get() = pluginDescriptor.isImplementationDetail
  override var forumUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.forumUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.forumUrl = value
      }
    }
  override var source: PluginSource? = null
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
  override var suggestedFeatures: Collection<String>
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedFeatures else emptyList()
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.suggestedFeatures = value
      }
    }
  override var reportPluginUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reportPluginUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.reportPluginUrl = value
      }
    }
  override var vendorDetails: PluginNodeVendorDetails?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.vendorDetails else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.vendorDetails = value
      }
    }

  override var screenShots: List<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.screenShots else null
    set(value) {
      if (pluginDescriptor is PluginNode && value != null) {
        pluginDescriptor.setScreenShots(value)
      }
    }
  override var reviewComments: ReviewsPageContainer?
    get() {
      if (pluginDescriptor is PluginNode) {
        val nodeReviewComments = pluginDescriptor.reviewComments ?: return null
        return ReviewsPageContainer.fromPageContainer(nodeReviewComments)
      }
      return null
    }
    set(value) {
      if (pluginDescriptor is PluginNode) {
        if (value == null) {
          pluginDescriptor.setReviewComments(PageContainer(0, 0))
          return
        }
        val container = PageContainer(value.myPageSize, value.myCurrentPage, value.items)
        pluginDescriptor.setReviewComments(container)
      }
    }
  override var dependencyNames: Collection<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.dependencyNames else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.dependencyNames = value
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
      pluginDescriptor.addDepends(id, optional)
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
  override var description: String?
    get() = pluginDescriptor.description
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.description = value
      }
    }
  override var category: String?
    get() = pluginDescriptor.category
    set(value) {
      if (pluginDescriptor is PluginNode && value != null) {
        pluginDescriptor.setCategory(value)
      }
    }
  override var isDeleted: Boolean
    get() = NewUiUtil.isDeleted(pluginDescriptor)
    set(value) {
      if (pluginDescriptor is IdeaPluginDescriptorImpl) {
        pluginDescriptor.isDeleted = value
      }
    }

  override var downloadUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.downloadUrl else null
    set(value) {
      if (pluginDescriptor is PluginNode) {
        pluginDescriptor.downloadUrl = value
      }
    }

  override val sinceBuild: String?
    get() = pluginDescriptor.sinceBuild

  override val isBundledUpdate: Boolean
    get() = DefaultUiPluginManagerController.isBundledUpdate(pluginDescriptor)

  override val untilBuild: String?
    get() = pluginDescriptor.untilBuild

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

  override fun toString(): String = "PluginUiModelAdapter($pluginDescriptor)"
}