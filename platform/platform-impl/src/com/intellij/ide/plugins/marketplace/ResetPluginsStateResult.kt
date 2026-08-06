// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.newui.PluginUpdateSourceState
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ResetPluginsStateResult(
  val changedEnabledStates: Map<PluginId, Boolean>,
  val updateSourceStatesToRevert: Map<PluginId, PluginUpdateSourceState>,
) {
  operator fun plus(remote: ResetPluginsStateResult): ResetPluginsStateResult {
    return ResetPluginsStateResult(changedEnabledStates + remote.changedEnabledStates,
                                   updateSourceStatesToRevert + remote.updateSourceStatesToRevert)
  }
}