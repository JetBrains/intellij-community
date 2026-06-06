// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * The terminal outcome of a completion request, carried by [RpcCompletionResponseEvent.CompletionFinished].
 *
 * Stating the outcome explicitly lets a consumer decide whether a request's results are safe to reuse without having
 * to reverse-engineer it from the presence/absence of other events: [SUCCEEDED] and [SKIPPED] are complete and final,
 * while [CANCELLED] and [FAILED] ended before the items were complete and must not be reused.
 */
@Serializable
enum class RpcCompletionFinishReason {

  /** All completion items were produced (preceded by [RpcCompletionResponseEvent.CompletionItemsFinished]). */
  SUCCEEDED,

  /**
   * The backend deliberately did not run completion: an autopopup whose `CompletionConfidence` decided this context
   * does not warrant an automatic popup (`CompletionPhase.shouldSkipAutoPopup`). A complete, final decision whose
   * result is legitimately empty — distinct from [SUCCEEDED] only on the wire / in logs.
   */
  SKIPPED,

  /** The request's stream ended before its items were complete (a new keystroke, a write action, scope cancellation). */
  CANCELLED,

  /** The backend could not run the request (setup failure or error). */
  FAILED,
}
