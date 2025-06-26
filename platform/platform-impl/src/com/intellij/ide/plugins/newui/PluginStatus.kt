// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class PluginInstallationState(
  val fullyInstalled: Boolean,
  val status: PluginStatus? = null
)

@ApiStatus.Internal
@Serializable
enum class PluginStatus {
  INSTALLED_AND_REQUIRED_RESTART, INSTALLED_WITHOUT_RESTART, UNINSTALLED_WITHOUT_RESTART, UPDATED_WITH_RESTART, UPDATED
}