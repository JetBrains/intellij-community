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

  fun operationStarted(sessionId: String, context: PluginOperationContext) {
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
  ) {
    synchronized(operations) {
      val state = checkNotNull(operations[context.operationId]) { "Plugin operation was not started: ${context.operationId}" }
      state.targetResults[target] = result
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
        )
      )
    }
  }

  private class OperationState(
    val sessionId: String,
    val targetResults: MutableMap<PluginSource, PluginOperationTerminalResult> = mutableMapOf(),
  )
}
