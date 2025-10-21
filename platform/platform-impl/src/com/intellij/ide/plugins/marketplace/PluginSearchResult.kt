// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
@Serializable
data class PluginSearchResult(
  @Transient val pluginModels: List<PluginUiModel> = emptyList(),
  val error: String? = null,
  val pluginDtos: List<PluginDto> = pluginModels.map(PluginDto::fromModel),
) {
  fun getPlugins(): List<PluginUiModel> = pluginModels.ifEmpty { pluginDtos }
}