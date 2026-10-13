// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.lang.ref.WeakReference

internal data class PluginManagerStateSnapshot(
  val pluginNonLoadReasons: Map<PluginId, PluginLoadingProblem>,
  val pluginsToDisable: Map<PluginId, String>,
  val pluginsToEnable: Map<PluginId, String>,
  val pluginLoadingProblemsForNotification: List<@Nls String>,
)

// for now this is a temporary place to store state that previously lived in PluginManagerCore, how it properly should be stored is not clear atm
@ApiStatus.Internal
@Service
class PluginManagerStateService {
  private class CachedState(
    val pluginSetReference: WeakReference<PluginSet>,
    val reportingPolicy: PluginLoadingErrorReportingPolicy,
    val state: PluginManagerStateSnapshot,
  )

  @Volatile
  private var cachedState: CachedState? = null

  fun getPluginNonLoadReason(pluginId: PluginId): PluginLoadingProblem? =
    getCurrentState()?.pluginNonLoadReasons?.get(pluginId)

  @Synchronized
  internal fun getCurrentState(
    reportingPolicy: PluginLoadingErrorReportingPolicy = PluginLoadingErrorReportingPolicy.forCurrentProduct(),
  ): PluginManagerStateSnapshot? {
    val pluginSet = PluginManagerCore.getPluginSetOrNull() ?: return null
    val cachedState = cachedState
    if (cachedState?.pluginSetReference?.get() === pluginSet && cachedState.reportingPolicy == reportingPolicy) {
      return cachedState.state
    }

    val state = calculateState(pluginSet, reportingPolicy)
    this.cachedState = CachedState(WeakReference(pluginSet), reportingPolicy, state)
    return state
  }

  internal fun calculateState(
    pluginSet: PluginSet,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ): PluginManagerStateSnapshot = PluginLoadingProblemAnalyzer.calculateState(pluginSet, reportingPolicy)

  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerStateService = service()
  }
}
