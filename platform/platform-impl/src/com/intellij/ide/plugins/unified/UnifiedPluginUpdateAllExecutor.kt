// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationContext
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CancellationException
import java.util.ArrayDeque
import java.util.UUID

internal class PageSessionPluginUpdateAllExecutor(
  private val operationContextFactory: (PluginUiModel) -> PluginOperationContext,
  private val installedPluginProvider: (PluginId) -> PluginUiModel?,
  private val updateStarter: (PluginUiModel, PluginUiModel, PluginOperationContext) -> Unit,
  private val runOnEdt: (() -> Unit) -> Unit,
  private val maximumActiveUpdates: Int = 3,
) : UnifiedPluginUpdateAllExecutor {
  private val lock = Any()
  private val pendingUpdates = ArrayDeque<PendingUpdate>()
  private val activeUpdates = HashMap<PluginId, ActiveUpdate>()
  private var drainScheduled = false
  private var closed = false

  init {
    require(maximumActiveUpdates > 0) { "The active Update All limit must be positive" }
  }

  override fun execute(request: UnifiedPluginUpdateAllRequest, callback: UnifiedPluginUpdateAllCallback) {
    synchronized(lock) {
      check(pendingUpdates.isEmpty() && activeUpdates.isEmpty()) { "An Update All request is already active" }
      if (closed) return
      request.updates.mapTo(pendingUpdates) { update -> PendingUpdate(update, callback) }
    }
    scheduleDrain()
  }

  override fun acceptOperationEvent(event: PluginModelEvent) {
    when (event) {
      is PluginModelEvent.OperationStarted -> {
        if (event.kind != PluginOperationKind.UPDATE) return
        synchronized(lock) {
          val active = activeUpdates[event.displayPluginId] ?: return
          if (active.operationId != event.operationId) return
        }
      }
      is PluginModelEvent.OperationFinished -> {
        if (event.kind != PluginOperationKind.UPDATE) return
        val released = synchronized(lock) {
          val active = activeUpdates[event.displayPluginId] ?: return@synchronized false
          if (active.operationId != event.operationId) return@synchronized false
          activeUpdates.remove(event.displayPluginId)
          true
        }
        if (released) scheduleDrain()
      }
      is PluginModelEvent.InventoryInvalidated -> {
        if (event.reason == PluginInventoryChangeReason.RESET) clearRequest()
      }
      is PluginModelEvent.OperationDependenciesScheduled -> Unit
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      clearRequestLocked()
    }
  }

  private fun clearRequest() {
    synchronized(lock) {
      clearRequestLocked()
    }
  }

  private fun clearRequestLocked() {
    pendingUpdates.clear()
    activeUpdates.clear()
  }

  private fun scheduleDrain() {
    val shouldSchedule = synchronized(lock) {
      if (closed || drainScheduled || pendingUpdates.isEmpty() || activeUpdates.size >= maximumActiveUpdates) {
        false
      }
      else {
        drainScheduled = true
        true
      }
    }
    if (shouldSchedule) runOnEdt(::drainOnEdt)
  }

  private fun drainOnEdt() {
    while (true) {
      val (pending, active) = synchronized(lock) {
        if (closed || pendingUpdates.isEmpty() || activeUpdates.size >= maximumActiveUpdates) {
          drainScheduled = false
          return
        }
        val update = pendingUpdates.removeFirst()
        val activeUpdate = ActiveUpdate()
        activeUpdates[update.model.pluginId] = activeUpdate
        update to activeUpdate
      }
      startUpdate(pending, active)
    }
  }

  private fun startUpdate(pending: PendingUpdate, active: ActiveUpdate) {
    val update = pending.model
    try {
      val operationContext = operationContextFactory(update)
      val requestIsActive = synchronized(lock) {
        if (activeUpdates[update.pluginId] !== active) false
        else {
          active.operationId = operationContext.operationId
          true
        }
      }
      if (!requestIsActive) return
      pending.callback.started(update.pluginId, operationContext.operationId)
      val installedPlugin = installedPluginProvider(update.pluginId)
                            ?: error("No installed plugin model for ${update.pluginId.idString}")
      updateStarter(installedPlugin, update, operationContext)
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (t: Throwable) {
      synchronized(lock) {
        if (activeUpdates[update.pluginId] === active) activeUpdates.remove(update.pluginId)
      }
      pending.callback.failed(update.pluginId, t)
    }
  }

  private data class PendingUpdate(
    val model: PluginUiModel,
    val callback: UnifiedPluginUpdateAllCallback,
  )

  private class ActiveUpdate(
    var operationId: UUID? = null,
  )
}
