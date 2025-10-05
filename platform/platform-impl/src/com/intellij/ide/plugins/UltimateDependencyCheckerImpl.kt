// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId

internal class UltimateDependencyCheckerImpl : UltimateDependencyChecker {
  override fun canBeEnabled(pluginId: PluginId): Boolean {
    if (!PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) return true

    val pluginIdMap = PluginManagerCore.buildPluginIdMap()
    val pluginSet = PluginManagerCore.getPluginSet()
    val contentModuleIdMap = pluginSet.buildContentModuleIdMap()

    return !pluginRequiresUltimatePlugin(pluginId, pluginIdMap, contentModuleIdMap)
  }
}