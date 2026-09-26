// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable

/**
 * A command sent frontend->backend over the single ordered [RpcCompletionService.commandFlow].
 *
 * This replaces the previously separate per-call RPCs (`closeSession`, `releaseRequests`, `prefixChanged`) and the per-lookup
 * `streamLookupEvents` flow. Those transports had no mutual ordering guarantee, so a session teardown (`CloseSession`) could overtake
 * the lookup's terminal [RpcLookupElementEvent.ItemSelected] on the backend and tear the completion session down before the forwarded
 * finishing action inserted the chosen item (wrong / missing insertion). Delivering every command on one ordered flow makes emission
 * order the delivery order, so `CloseSession` can never be processed before the `ItemSelected` that precedes it.
 *
 * Symmetric to [RpcCompletionSessionEvent] carried by [RpcCompletionService.responseFlow] in the opposite direction.
 */
@Serializable
sealed interface RpcCompletionCommand {

  /** A lookup mirror event (state change / item selected / cancel). Carries its own request/project id. */
  @Serializable
  data class LookupEvent(
    val event: RpcLookupElementEvent,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("LookupEvent") {
      field("event", event)
    }
  }

  /** Closes a completion session on the backend, disposing the session and all of its request sessions. */
  @Serializable
  data class CloseSession(
    val sessionId: RpcCompletionSessionId,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("CloseSession") {
      field("sessionId", sessionId)
    }
  }

  /** Releases backend request sessions the frontend dropped from its cache (no longer reusable). */
  @Serializable
  data class ReleaseRequests(
    val sessionId: RpcCompletionSessionId,
    val requestIds: List<RpcCompletionRequestId>,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("ReleaseRequests") {
      field("sessionId", sessionId)
      field("requestIds", requestIds)
    }
  }

  /** Notifies the backend that the prefix of the given request changed. */
  @Serializable
  data class PrefixChanged(
    val requestId: RpcCompletionRequestId,
    val update: RpcPrefixUpdate,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("PrefixChanged") {
      field("requestId", requestId)
      field("update", update)
    }
  }

  /**
   * Opens a completion session on the backend (spans a single lookup and a series of requests). Idempotent; also
   * created lazily by the first [StartRequest], so an explicit open just sets up session-scoped state eagerly.
   */
  @Serializable
  data class OpenSession(
    val sessionId: RpcCompletionSessionId,
    val editorId: EditorId,
    val projectId: ProjectId,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("OpenSession") {
      field("sessionId", sessionId)
      field("editorId", editorId)
    }
  }

  /**
   * Starts a completion request within a session. The response streams back on
   * [com.intellij.platform.completion.common.split.RpcCompletionService.responseFlow], tagged with the request's
   * session id. Sent only after the response flow is established, so the backend response channel exists when it runs.
   */
  @Serializable
  data class StartRequest(
    val request: RpcCompletionRequest,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("StartRequest") {
      field("request", request)
    }
  }

  /**
   * Requests ModCommand computation for a completion item; the backend responds with
   * [RpcCompletionResponseEvent.ModCommandResults] on the response flow.
   */
  @Serializable
  data class ScheduleModCommand(
    val requestId: RpcCompletionRequestId,
    val completionItemId: RpcCompletionItemId,
  ) : RpcCompletionCommand {
    override fun toString(): String = buildToString("ScheduleModCommand") {
      field("requestId", requestId)
      field("completionItemId", completionItemId)
    }
  }
}
