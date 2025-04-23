// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.getTags
import com.intellij.ide.plugins.newui.NewUiUtil
import com.intellij.ide.plugins.PluginManagerCore.getUnfulfilledOsRequirement
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

  override val isIncompatibleWithCurrentOs: Boolean
    get() {
      if ("com.jetbrains.kmm" != pluginId.idString || SystemInfoRt.isMac) return true
      return getUnfulfilledOsRequirement(pluginDescriptor) == null
    }

  override val canBeEnabled: Boolean
    get() {
      return PluginManagementPolicy.getInstance().canEnablePlugin(pluginDescriptor)
    }

  override val name: String = pluginDescriptor.name

  override val requiresUpgrade: Boolean
    get() {
      return pluginDescriptor is PluginNode && pluginDescriptor.suggestedCommercialIde != null
    }

  override val tags: List<String>
    get() {
      return if (pluginDescriptor is PluginNode) pluginDescriptor.tags else emptyList()
    }

  override val isBundled: Boolean
    get() = pluginDescriptor.isBundled

  override val version: String?
    get() = pluginDescriptor.version

  override val vendor: String?
    get() = pluginDescriptor.vendor

  override val organization: String?
    get() = pluginDescriptor.organization

  override val downloads: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.downloads else null

  override val rating: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.rating else null
  override val productCode: String?
    get() = pluginDescriptor.productCode

  override val size: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.size else null
  override val isDeleted: Boolean
    get() = NewUiUtil.isDeleted(pluginDescriptor)
    
  override val dependencies: List<PluginDependencyModel>
    get() = pluginDescriptor.dependencies.map { PluginDependencyModel(it.pluginId, it.isOptional) }

  override val isLicenseOptional: Boolean
    get() = pluginDescriptor.isLicenseOptional

  override val suggestedCommercialIde: String?
    get() {
      return if (pluginDescriptor is PluginNode) pluginDescriptor.suggestedCommercialIde else null
    }

  override val source: PluginSource = PluginSource.LOCAL

  // URL-related properties
  override val forumUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.forumUrl else null

  override val licenseUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.licenseUrl else null

  override val bugtrackerUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.bugtrackerUrl else null

  override val documentationUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.documentationUrl else null

  override val sourceCodeUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.sourceCodeUrl else null

  override val reportPluginUrl: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reportPluginUrl else null

  override val dependencyNames: Collection<String>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.dependencyNames else null

  override val repositoryName: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.repositoryName else null

  override val reviewComments: PageContainer<PluginReviewComment>?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.reviewComments else null

  override val verifiedName: String?
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.verifiedName else null

  override val isVerified: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isVerified else false

  override val isTrader: Boolean
    get() = if (pluginDescriptor is PluginNode) pluginDescriptor.isTrader else false

  override fun getDescriptor(): IdeaPluginDescriptor = pluginDescriptor
}