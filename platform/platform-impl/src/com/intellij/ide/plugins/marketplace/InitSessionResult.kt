// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.ifEmpty

@Serializable
@ApiStatus.Internal
data class InitSessionResult(
  @Transient val visiblePlugins: List<PluginUiModel> = emptyList(),
  val pluginStates: Map<PluginId, Boolean?> = emptyMap(),
  val visiblePluginDtos: List<PluginDto> = visiblePlugins.map(PluginDto::fromModel),
) {
  fun getVisiblePluginsList(): List<PluginUiModel> = visiblePlugins.ifEmpty { visiblePluginDtos }
}