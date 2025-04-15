// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId

/**
 * A lightweight model for representing plugin information in the UI.
 * This interface contains only the subset of plugin metadata needed for display purposes.
 */
interface PluginUiModel {
  val pluginId: PluginId

  val isIncompatibleWithCurrentOs: Boolean

  val canBeEnabled: Boolean

  val name: String

  val source: PluginSource
  
  /**
   * Returns true if the plugin requires an upgrade to a commercial IDE.
   */
  val requiresUpgrade: Boolean

  /**
   * Java compatibility method. Going to be removed after refactoring is done.
   */
  fun getDescriptor(): IdeaPluginDescriptor = this.getPluginDescriptor()
}

fun PluginUiModel.getPluginDescriptor(): IdeaPluginDescriptor {
  if (this is PluginUiModelAdapter) return pluginDescriptor
  throw IllegalStateException("PluginUiModelAdapter expected")
}

enum class PluginSource {
  LOCAL, REMOTE
}