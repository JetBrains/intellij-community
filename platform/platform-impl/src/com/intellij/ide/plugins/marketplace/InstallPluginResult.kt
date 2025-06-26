// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
class InstallPluginResult {
  var installedDescriptor: PluginDto? = null
  var success: Boolean = true
  var cancel: Boolean = false
  var showErrors: Boolean = true
  var restartRequired: Boolean = true
  var dynamicRestartRequired = false
  var pluginsToDisable: Set<PluginId> = emptySet()
  var errors: Map<PluginId, CheckErrorsResult> = emptyMap()
}