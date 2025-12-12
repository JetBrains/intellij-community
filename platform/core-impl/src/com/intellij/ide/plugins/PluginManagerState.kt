// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginManagerState internal constructor(
  val pluginSet: PluginSet,
  val pluginIdsToDisable: Set<PluginId>,
  val pluginIdsToEnable: Set<PluginId>,
  val incompleteIdMapForLogging: Map<PluginId, PluginMainDescriptor>, // TODO refactor
  val shadowedBundledPlugins: Set<PluginId>,
)