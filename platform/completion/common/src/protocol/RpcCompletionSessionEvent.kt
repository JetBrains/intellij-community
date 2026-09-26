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
}
