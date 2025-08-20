// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
@IntellijInternalApi
class InstallPluginResult {
  var installedDescriptor: PluginDto? = null
  var success: Boolean = true
  var cancel: Boolean = false
  var showErrors: Boolean = true
  var restartRequired: Boolean = true
  var dynamicRestartRequired = false
  var pluginsToDisable: Set<PluginId> = emptySet()
  var pluginsToEnable: Set<PluginId> = emptySet()
  var errors: Map<PluginId, CheckErrorsResult> = emptyMap()
  var disabledPlugins: List<String> = emptyList()
  var disabledDependants: List<String> = emptyList()
  var allowInstallWithoutRestart: Boolean = true
  var dynamicUiPlugin: Boolean = false
  companion object {
    val FAILED = InstallPluginResult().apply { success = false }
  }
}