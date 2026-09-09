// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

@ApiStatus.Internal
sealed interface PluginModelEvent {
  /** Empty [pluginIds] means that the complete inventory may have changed. */
  data class InventoryInvalidated(
    val reason: PluginInventoryChangeReason,
    val pluginIds: Set<PluginId>,
  ) : PluginModelEvent

  data class OperationStarted(
    val sessionId: String,
    val operationId: UUID,
    val displayPluginId: PluginId,
    val presentationModel: PluginUiModel,
    val target: PluginSource,
    val kind: PluginOperationKind,
  ) : PluginModelEvent

  data class OperationFinished(
    val sessionId: String,
    val operationId: UUID,
    val displayPluginId: PluginId,
    val target: PluginSource,
    val kind: PluginOperationKind,
    val result: PluginOperationTerminalResult,
    val installedPlugins: List<PluginUiModel> = emptyList(),
    val restartRequired: Boolean = false,
  ) : PluginModelEvent

  data class OperationDependenciesScheduled(
    val sessionId: String,
    val operationId: UUID,
    val displayPluginId: PluginId,
    val dependencies: List<PluginUiModel>,
    val target: PluginSource,
    val kind: PluginOperationKind,
  ) : PluginModelEvent
}

@ApiStatus.Internal
enum class PluginInventoryChangeReason {
  APPLY,
  RESET,
  INSTALL,
  UPDATE,
  UNINSTALL,
  ENABLE_DISABLE,
  INSTALL_FROM_DISK,
}

@ApiStatus.Internal
enum class PluginOperationKind {
  INSTALL,
  UPDATE,
}

@ApiStatus.Internal
enum class PluginOperationTerminalResult {
  SUCCEEDED,
  CANCELLED,
  FAILED,
}

@ApiStatus.Internal
class PluginOperationContext private constructor(
  val operationId: UUID,
  val displayPluginId: PluginId,
  val target: PluginSource,
  val kind: PluginOperationKind,
) {
  internal val expectedTargets: Set<PluginSource> = when (target) {
    PluginSource.BOTH -> setOf(PluginSource.LOCAL, PluginSource.REMOTE)
    else -> setOf(target)
  }

  companion object {
    @JvmStatic
    fun create(
      displayPluginId: PluginId,
      target: PluginSource,
      kind: PluginOperationKind,
    ): PluginOperationContext {
      return PluginOperationContext(UUID.randomUUID(), displayPluginId, target, kind)
    }
  }
}

/** Receives page-session model events on the thread which completes the corresponding operation. */
@ApiStatus.Internal
fun interface PluginModelEventSink {
  fun onEvent(event: PluginModelEvent)

  companion object {
    @JvmField
    val NONE: PluginModelEventSink = PluginModelEventSink { }
  }
}

internal class PluginModelEventPublisher(private val sink: PluginModelEventSink) {
  private val operations = mutableMapOf<UUID, OperationState>()

  fun inventoryInvalidated(
    reason: PluginInventoryChangeReason,
    pluginIds: Collection<PluginId> = emptySet(),
  ) {
    sink.onEvent(PluginModelEvent.InventoryInvalidated(reason, pluginIds.toSet()))
  }

  fun operationStarted(sessionId: String, context: PluginOperationContext, presentationModel: PluginUiModel) {
    require(presentationModel.pluginId == context.displayPluginId) {
      "Plugin operation display ID ${context.displayPluginId} does not match presentation model ${presentationModel.pluginId}"
    }
    synchronized(operations) {
      if (operations.containsKey(context.operationId)) {
        return
      }
      operations[context.operationId] = OperationState(sessionId)
      sink.onEvent(
        PluginModelEvent.OperationStarted(
          sessionId = sessionId,
          operationId = context.operationId,
          displayPluginId = context.displayPluginId,
          presentationModel = presentationModel,
          target = context.target,
          kind = context.kind,
        )
      )
    }
  }

  fun operationTargetFinished(
    context: PluginOperationContext,
    target: PluginSource,
    result: PluginOperationTerminalResult,
    installedPlugins: Collection<PluginUiModel> = emptyList(),
    restartRequired: Boolean = false,
  ) {
    synchronized(operations) {
      val state = checkNotNull(operations[context.operationId]) { "Plugin operation was not started: ${context.operationId}" }
      state.targetResults[target] = result
      installedPlugins.forEach { plugin -> state.installedPlugins.putIfAbsent(plugin.pluginId, plugin) }
      state.restartRequired = state.restartRequired || restartRequired
    }
  }

  fun operationDependenciesScheduled(
    context: PluginOperationContext,
    dependencies: Collection<PluginUiModel>,
  ) {
    synchronized(operations) {
      val state = checkNotNull(operations[context.operationId]) { "Plugin operation was not started: ${context.operationId}" }
      val newDependencies = dependencies.filter { dependency ->
        dependency.pluginId != context.displayPluginId &&
        state.scheduledDependencies.putIfAbsent(dependency.pluginId, dependency) == null
      }
      if (newDependencies.isEmpty()) return
      sink.onEvent(
        PluginModelEvent.OperationDependenciesScheduled(
          sessionId = state.sessionId,
          operationId = context.operationId,
          displayPluginId = context.displayPluginId,
          dependencies = newDependencies,
          target = context.target,
          kind = context.kind,
        )
      )
    }
  }

  fun operationFinished(context: PluginOperationContext) {
    synchronized(operations) {
      val state = operations.remove(context.operationId) ?: return
      val results = state.targetResults
      val result = when {
        PluginOperationTerminalResult.FAILED in results.values -> PluginOperationTerminalResult.FAILED
        !results.keys.containsAll(context.expectedTargets) -> PluginOperationTerminalResult.CANCELLED
        PluginOperationTerminalResult.CANCELLED in results.values -> PluginOperationTerminalResult.CANCELLED
        else -> PluginOperationTerminalResult.SUCCEEDED
      }
      sink.onEvent(
        PluginModelEvent.OperationFinished(
          sessionId = state.sessionId,
          operationId = context.operationId,
          displayPluginId = context.displayPluginId,
          target = context.target,
          kind = context.kind,
          result = result,
          installedPlugins = state.installedPlugins.values.toList(),
          restartRequired = result == PluginOperationTerminalResult.SUCCEEDED && state.restartRequired,
        )
      )
    }
  }

  private class OperationState(
    val sessionId: String,
    val targetResults: MutableMap<PluginSource, PluginOperationTerminalResult> = mutableMapOf(),
    val installedPlugins: LinkedHashMap<PluginId, PluginUiModel> = LinkedHashMap(),
    val scheduledDependencies: LinkedHashMap<PluginId, PluginUiModel> = LinkedHashMap(),
    var restartRequired: Boolean = false,
  )
}
