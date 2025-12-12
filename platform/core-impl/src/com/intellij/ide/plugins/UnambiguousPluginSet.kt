// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

/**
 * An instance of [UnambiguousPluginSet] represents a set of plugins that do not conflict
 * with each other by declared plugin and content module ids (including plugin aliases).
 */
@ApiStatus.Internal
interface UnambiguousPluginSet {
  val plugins: List<PluginMainDescriptor>

  /**
   * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
   * in which case it may refer either to the plugin descriptor module or to the plugin content module.
   */
  fun resolvePluginId(id: PluginId): PluginModuleDescriptor?

  fun resolveContentModuleId(id: PluginModuleId): ContentModuleDescriptor?

  /**
   * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
   * in which case it may refer either to the plugin descriptor module or to the plugin content module.
   */
  fun getFullPluginIdMapping(): Map<PluginId, PluginModuleDescriptor>

  fun getFullContentModuleIdMapping(): Map<PluginModuleId, ContentModuleDescriptor>

  companion object
}