// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ApplyPluginsStateResult(
  val pluginsToEnable: Set<PluginId> = emptySet(),
  var needRestart: Boolean = false
)