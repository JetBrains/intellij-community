// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * A top-level event sent over [com.intellij.platform.completion.common.split.RpcCompletionService.responseFlow].
 *
 * Unlike [RpcCompletionResponseEvent], which is scoped to a single completion request, a session event is scoped to a
 * whole completion session ([RpcCompletionSessionId]) and therefore survives per-request restarts. Per-request events
 * are carried as [Response]; session-scoped events (which must not be discarded when a request is dropped) can be added
 * as additional subtypes.
 */
@Serializable
sealed interface RpcCompletionSessionEvent {
  val sessionId: RpcCompletionSessionId

  /**
   * Carries a per-request [RpcCompletionResponseEvent] together with the id of the session it belongs to.
   */
  @Serializable
  data class Response(
    override val sessionId: RpcCompletionSessionId,
    val event: RpcCompletionResponseEvent,
  ) : RpcCompletionSessionEvent

  /**
   * Tells the frontend that the backend has started a fresh request ([request]) for the session because the
   * previous request was cancelled by a write action, and that completion should be restarted to pick it up.
   *
   * The full [RpcCompletionRequest] is carried (not just its id) because the frontend is not running a completion
   * contribution when this arrives: it has no `CompletionParameters` to build a request from. The frontend registers
   * a cache entry for [request] so the request's subsequent [Response] events are retained (not dropped as unknown),
   * then restarts completion so the next contribution adopts that already-started backend session instead of issuing
   * a brand-new request. This event is enqueued before any of the new request's events, so the cache entry exists in
   * time.
   *
   * [restartedFromRequestId] is the request this restart supersedes (the one the write action cancelled). The frontend
   * honors the restart only while that is still its active request; if it has already moved on to a newer request
   * (e.g. the user kept typing and a new request session started), the restart is for a request it has superseded and
   * is ignored.
   */
  @Serializable
  data class RestartCompletion(
    override val sessionId: RpcCompletionSessionId,
    val request: RpcCompletionRequest,
    val restartedFromRequestId: RpcCompletionRequestId,
  ) : RpcCompletionSessionEvent
}
