// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
@Serializable
class InstallPluginRequest(
  val sessionId: String,
  val pluginId: PluginId,
  val pluginsToInstall: List<PluginUiModel>,
  val allowInstallWithoutRestart: Boolean,
  val finishDynamicInstallationWithoutUi: Boolean,
  val needRestart: Boolean,
)