// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * The terminal outcome of a completion request, carried by [RpcCompletionResponseEvent.CompletionFinished].
 *
 * Stating the outcome explicitly lets a consumer decide whether a request's results are safe to reuse without having
 * to reverse-engineer it from the presence/absence of other events: [SUCCEEDED] and [SKIPPED] are complete and final,
 * while [CANCELLED], [FAILED] and [RestartingAfterWriteAction] ended before the items were complete and must not be
 * reused.
 */
@Serializable
sealed interface RpcCompletionFinishReason {

  /** All completion items were produced (preceded by [RpcCompletionResponseEvent.CompletionItemsFinished]). */
  @Serializable
  data object SUCCEEDED : RpcCompletionFinishReason

  /**
   * The backend deliberately did not run completion: an autopopup whose `CompletionConfidence` decided this context
   * does not warrant an automatic popup (`CompletionPhase.shouldSkipAutoPopup`). A complete, final decision whose
   * result is legitimately empty — distinct from [SUCCEEDED] only on the wire / in logs.
   */
  @Serializable
  data object SKIPPED : RpcCompletionFinishReason

  /** The request's stream ended before its items were complete (a new keystroke, a write action, scope cancellation). */
  @Serializable
  data object CANCELLED : RpcCompletionFinishReason

  /** The backend could not run the request (setup failure or error). */
  @Serializable
  data object FAILED : RpcCompletionFinishReason

  /**
   * The request was canceled by a write action, and the backend immediately restarted it as [request] (a fresh request
   * for the same session, with a new id and recomputed editor/document versions). This terminal *is* the restart
   * announcement: it carries the full [RpcCompletionRequest] so the frontend can adopt the already-started backend
   * session instead of issuing a brand-new request, and the request it restarts is the [RpcCompletionResponseEvent.CompletionFinished.requestId]
   * carrying this reason. The frontend must keep the lookup alive (the restart drives recovery) and must not reuse this
   * request's partial results.
   *
   * Because the backend forwards this terminal while still holding its completion mutex, it is enqueued before any of
   * [request]'s events — so the frontend registers a cache entry for [request] in time.
   */
  @Serializable
  data class RestartingAfterWriteAction(val request: RpcCompletionRequest) : RpcCompletionFinishReason
}
