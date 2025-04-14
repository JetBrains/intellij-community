// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.getUnfulfilledOsRequirement
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfoRt

/**
 * A temporary class used to eliminate "runtime" PluginDescriptor usages in the UI. It will later be replaced with frontend and backend implementations.
 */
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

  override val source: PluginSource = PluginSource.LOCAL
}