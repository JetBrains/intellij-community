// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

// todo merge into PluginSetState?
@ApiStatus.Internal
data class PluginManagerState internal constructor(
  @JvmField val pluginSet: PluginSet,
  @JvmField val pluginIdsToDisable: Set<PluginId>,
  @JvmField val pluginIdsToEnable: Set<PluginId>,
)