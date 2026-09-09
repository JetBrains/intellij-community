// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal sealed interface UnifiedPluginUpdateAllPresentation {
  data object Hidden : UnifiedPluginUpdateAllPresentation

  data object Available : UnifiedPluginUpdateAllPresentation

  data class Running(val prepared: Int, val total: Int) : UnifiedPluginUpdateAllPresentation

  data object RestartRequired : UnifiedPluginUpdateAllPresentation

  data object Updated : UnifiedPluginUpdateAllPresentation

  data object Failed : UnifiedPluginUpdateAllPresentation
}

internal enum class UnifiedPluginUpdateAllTargetStatus {
  Active,
  Failed,
}

internal data class UnifiedPluginUpdateAllTarget(
  val model: PluginUiModel,
  val status: UnifiedPluginUpdateAllTargetStatus,
)

internal data class UnifiedPluginUpdateAllState(
  val presentation: UnifiedPluginUpdateAllPresentation = UnifiedPluginUpdateAllPresentation.Hidden,
  val targets: List<UnifiedPluginUpdateAllTarget> = emptyList(),
)

internal data class UnifiedPluginUpdateAllRequest(
  val generation: Long,
  val updates: List<PluginUiModel>,
) {
  init {
    require(generation > 0) { "Update All generation must be positive" }
    require(updates.isNotEmpty()) { "Update All request must contain an update" }
    require(updates.distinctBy(PluginUiModel::pluginId).size == updates.size) {
      "Update All request plugin IDs must be unique"
    }
  }

  val pluginIds: Set<PluginId> = updates.mapTo(LinkedHashSet(), PluginUiModel::pluginId)
}

internal interface UnifiedPluginUpdateAllCallback {
  fun started(pluginId: PluginId, operationId: UUID) = Unit

  fun prepared(pluginId: PluginId, restartRequired: Boolean)

  fun failed(pluginId: PluginId, cause: Throwable)
}

internal fun interface UnifiedPluginUpdateAllExecutor {
  fun execute(request: UnifiedPluginUpdateAllRequest, callback: UnifiedPluginUpdateAllCallback)

  fun acceptOperationEvent(event: PluginModelEvent) = Unit

  fun close() = Unit
}

/**
 * Owns one page session's Update All request and presentation state.
 *
 * The internal lock serializes update snapshots, executor callbacks, and user requests.
 */
internal class UnifiedPluginUpdateAllController(
  private val executor: UnifiedPluginUpdateAllExecutor,
  private val targetSink: (List<UnifiedPluginUpdateAllTarget>) -> Unit = {},
) : AutoCloseable {
  private val lock = Any()
  private val mutableState = MutableStateFlow(UnifiedPluginUpdateAllState())
  private var latestUpdates: List<PluginUiModel> = emptyList()
  private var activeRequest: ActiveRequest? = null
  private var nextGeneration = 0L
  private var preparedRestartRequired = false
  private var closed = false

  val state: StateFlow<UnifiedPluginUpdateAllState> = mutableState.asStateFlow()

  fun acceptUpdates(event: PluginUpdatesEvent) {
    synchronized(lock) {
      if (closed) return
      latestUpdates = enabledUpdateSnapshot(event)
      if (activeRequest == null) {
        publishIdleState()
      }
    }
  }

  fun acceptOperationEvent(event: PluginModelEvent) {
    try {
      synchronized(lock) {
        if (closed) return
        if (event is PluginModelEvent.InventoryInvalidated && event.reason == PluginInventoryChangeReason.RESET) {
          activeRequest = null
          preparedRestartRequired = false
          publishSnapshotAvailability()
          return
        }
        val request = activeRequest ?: return
        when (event) {
          is PluginModelEvent.OperationStarted -> {
            if (event.kind != PluginOperationKind.UPDATE || event.displayPluginId !in request.request.pluginIds) return
            if (event.displayPluginId in request.terminalPluginIds) return
            val operationId = request.operationIds[event.displayPluginId]
            if (operationId != null && operationId != event.operationId) return
            if (operationId == null) request.operationIds[event.displayPluginId] = event.operationId
          }
          is PluginModelEvent.OperationFinished -> {
            if (event.kind != PluginOperationKind.UPDATE) return
            if (request.operationIds[event.displayPluginId] != event.operationId) return
            if (event.result == PluginOperationTerminalResult.SUCCEEDED) {
              acceptPrepared(request, event.displayPluginId, event.restartRequired)
            }
            else {
              acceptFailure(request, event.displayPluginId, null)
            }
          }
          is PluginModelEvent.InventoryInvalidated,
          is PluginModelEvent.OperationDependenciesScheduled -> Unit
        }
      }
    }
    finally {
      executor.acceptOperationEvent(event)
    }
  }

  fun requestUpdateAll() {
    val execution = synchronized(lock) {
      if (closed || !mutableState.value.presentation.canStartUpdateAll()) return
      val updates = if (mutableState.value.presentation == UnifiedPluginUpdateAllPresentation.Failed) {
        mutableState.value.targets.filter { it.status == UnifiedPluginUpdateAllTargetStatus.Failed }.map { it.model }
      }
      else {
        latestUpdates
      }
      if (updates.isEmpty()) return
      val request = UnifiedPluginUpdateAllRequest(++nextGeneration, updates.toList())
      val active = ActiveRequest(request)
      activeRequest = active
      publishRequestState(active)
      Execution(request, callback(request.generation))
    }

    try {
      executor.execute(execution.request, execution.callback)
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (t: Throwable) {
      acceptExecutorFailure(execution.request.generation, t)
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      activeRequest = null
    }
    executor.close()
  }

  private fun callback(generation: Long): UnifiedPluginUpdateAllCallback {
    return object : UnifiedPluginUpdateAllCallback {
      override fun started(pluginId: PluginId, operationId: UUID) {
        acceptStarted(generation, pluginId, operationId)
      }

      override fun prepared(pluginId: PluginId, restartRequired: Boolean) {
        acceptPrepared(generation, pluginId, restartRequired)
      }

      override fun failed(pluginId: PluginId, cause: Throwable) {
        acceptFailure(generation, pluginId, cause)
      }
    }
  }

  private fun acceptStarted(generation: Long, pluginId: PluginId, operationId: UUID) {
    synchronized(lock) {
      val request = activeRequest?.takeIf { !closed && it.request.generation == generation } ?: return
      if (pluginId !in request.request.pluginIds || pluginId in request.terminalPluginIds) return
      request.operationIds.putIfAbsent(pluginId, operationId)
    }
  }

  private fun acceptPrepared(generation: Long, pluginId: PluginId, restartRequired: Boolean) {
    synchronized(lock) {
      val request = activeRequest?.takeIf { !closed && it.request.generation == generation } ?: return
      acceptPrepared(request, pluginId, restartRequired)
    }
  }

  private fun acceptPrepared(request: ActiveRequest, pluginId: PluginId, restartRequired: Boolean) {
    if (pluginId !in request.request.pluginIds || pluginId in request.terminalPluginIds) return
    request.preparedPluginIds.add(pluginId)
    preparedRestartRequired = preparedRestartRequired || restartRequired
    publishAfterTargetFinished(request)
  }

  private fun acceptFailure(generation: Long, pluginId: PluginId, cause: Throwable) {
    synchronized(lock) {
      val request = activeRequest?.takeIf { !closed && it.request.generation == generation } ?: return
      acceptFailure(request, pluginId, cause)
    }
  }

  private fun acceptFailure(request: ActiveRequest, pluginId: PluginId, cause: Throwable?) {
    if (pluginId !in request.request.pluginIds || pluginId in request.terminalPluginIds) return
    if (cause != null) {
      LOG.warn("Update All failed to start an update for ${pluginId.idString}", cause)
    }
    request.failedPluginIds.add(pluginId)
    publishAfterTargetFinished(request)
  }

  private fun acceptExecutorFailure(generation: Long, cause: Throwable) {
    synchronized(lock) {
      val request = activeRequest?.takeIf { !closed && it.request.generation == generation } ?: return
      LOG.warn("Update All executor failed", cause)
      request.failedPluginIds.addAll(request.request.pluginIds - request.terminalPluginIds)
      publishAfterTargetFinished(request)
    }
  }

  private fun publishAfterTargetFinished(request: ActiveRequest) {
    if (!request.terminalPluginIds.containsAll(request.request.pluginIds)) {
      publishRequestState(request)
      return
    }

    activeRequest = null
    if (request.failedPluginIds.isNotEmpty()) {
      val failedTargets = request.request.updates.filter { it.pluginId in request.failedPluginIds }.map { model ->
        UnifiedPluginUpdateAllTarget(model, UnifiedPluginUpdateAllTargetStatus.Failed)
      }
      publish(UnifiedPluginUpdateAllState(UnifiedPluginUpdateAllPresentation.Failed, failedTargets))
    }
    else {
      publish(UnifiedPluginUpdateAllState(
        if (preparedRestartRequired) UnifiedPluginUpdateAllPresentation.RestartRequired
        else UnifiedPluginUpdateAllPresentation.Updated
      ))
    }
  }

  private fun publishRequestState(request: ActiveRequest) {
    val unresolvedTargets = request.request.updates.filter { it.pluginId !in request.preparedPluginIds }.map { model ->
      UnifiedPluginUpdateAllTarget(
        model = model,
        status = if (model.pluginId in request.failedPluginIds) {
          UnifiedPluginUpdateAllTargetStatus.Failed
        }
        else {
          UnifiedPluginUpdateAllTargetStatus.Active
        },
      )
    }
    publish(UnifiedPluginUpdateAllState(
      UnifiedPluginUpdateAllPresentation.Running(request.preparedPluginIds.size, request.request.updates.size),
      unresolvedTargets,
    ))
  }

  private fun publishIdleState() {
    val currentPresentation = mutableState.value.presentation
    publish(when (currentPresentation) {
      UnifiedPluginUpdateAllPresentation.RestartRequired,
      UnifiedPluginUpdateAllPresentation.Updated,
      UnifiedPluginUpdateAllPresentation.Failed -> mutableState.value
      else -> snapshotAvailabilityState()
    })
  }

  private fun publishSnapshotAvailability() {
    publish(snapshotAvailabilityState())
  }

  private fun snapshotAvailabilityState(): UnifiedPluginUpdateAllState {
    return if (latestUpdates.isEmpty()) {
      UnifiedPluginUpdateAllState()
    }
    else {
      UnifiedPluginUpdateAllState(UnifiedPluginUpdateAllPresentation.Available)
    }
  }

  private fun publish(state: UnifiedPluginUpdateAllState) {
    val previousTargets = mutableState.value.targets
    mutableState.value = state
    if (previousTargets != state.targets) {
      targetSink(state.targets)
    }
  }

  private data class ActiveRequest(
    val request: UnifiedPluginUpdateAllRequest,
    val operationIds: MutableMap<PluginId, UUID> = HashMap(),
    val preparedPluginIds: MutableSet<PluginId> = LinkedHashSet(),
    val failedPluginIds: MutableSet<PluginId> = LinkedHashSet(),
  ) {
    val terminalPluginIds: Set<PluginId>
      get() = preparedPluginIds + failedPluginIds
  }

  private data class Execution(
    val request: UnifiedPluginUpdateAllRequest,
    val callback: UnifiedPluginUpdateAllCallback,
  )

  private companion object {
    val LOG = logger<UnifiedPluginUpdateAllController>()
  }
}

internal fun enabledUpdateSnapshot(event: PluginUpdatesEvent): List<PluginUiModel> {
  return event.enabledUpdates.distinctBy(PluginUiModel::pluginId)
}

private fun UnifiedPluginUpdateAllPresentation.canStartUpdateAll(): Boolean {
  return this == UnifiedPluginUpdateAllPresentation.Available || this == UnifiedPluginUpdateAllPresentation.Failed
}
