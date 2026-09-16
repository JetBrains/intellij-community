// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class PluginRowInput(
  val installedPlugin: PluginUiModel?,
  val installationState: PluginInstallationState,
  val errors: List<HtmlChunk>,
  val updateDescriptor: PluginUiModel?,
  val enabled: Boolean,
  val restrictedByProduct: Boolean,
  val operationInProgress: Boolean = false,
  val detailsProgress: PluginProgressState? = null,
  val preparedUpdate: PluginPreparedUpdateState? = null,
)

@ApiStatus.Internal
data class PluginPreparedUpdateState(val restartRequired: Boolean)

@ApiStatus.Internal
sealed interface PluginProgressState {
  data object Indeterminate : PluginProgressState

  data class Determinate(val fraction: Double) : PluginProgressState
}
