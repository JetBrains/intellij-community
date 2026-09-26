// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.completion.common.protocol.modCommand.RpcModCommandResult
import kotlinx.serialization.Serializable

/**
 * Represents a single backend response event.
 */
@Serializable
sealed interface RpcCompletionResponseEvent {
  val requestId: RpcCompletionRequestId
  fun debugToString(): String

  /**
   * This event is sent when new completion items become available.
   * Contains new items and the order for ALL items in the current completion session.
   */
  @Serializable
  data class NewItems(
    override val requestId: RpcCompletionRequestId,
    val newItems: List<RpcCompletionItem> = emptyList(),
    val completionArrangement: RpcCompletionArrangement,
  ) : RpcCompletionResponseEvent {
    override fun toString(): String = buildToString("NewItems") {
      fieldWithEmptyDefault("newItems", newItems)
      field("completionArrangement", completionArrangement)
    }

    override fun debugToString(): String = "NewItems {size=${newItems.size}}"
  }

  /**
   * This event is sent when all items are already emitted,
   * but the prefix changed without completion restarting, and we need to re-sort items.
   */
  @Serializable
  data class NewArrangement(
    override val requestId: RpcCompletionRequestId,
    val completionArrangement: RpcCompletionArrangement,
  ) : RpcCompletionResponseEvent {
    override fun toString(): String = buildToString("NewArrangement") {
      field("completionArrangement", completionArrangement)
    }

    override fun debugToString(): String = "NewArrangement"
  }

  @Serializable
  data class ExpensivePresentations(
    override val requestId: RpcCompletionRequestId,
    val presentations: List<RpcCompletionExpensivePresentation>,
  ) : RpcCompletionResponseEvent {
    override fun debugToString(): String = "ExpensivePresentations {size=${presentations.size}}"
  }

  /**
   * This event is sent when ModCommand computation results are available.
   * Similar to [ExpensivePresentations], but for ModCommand (from [com.intellij.modcompletion.ModCompletionItem]).
   */
  @Serializable
  data class ModCommandResults(
    override val requestId: RpcCompletionRequestId,
    val results: List<RpcModCommandResult>,
  ) : RpcCompletionResponseEvent {
    override fun debugToString(): String = "ModCommandResults {size=${results.size}}"
  }

  /**
   * This event is sent when all completion items are sent.
   * After it is sent, the backend can send only updates for the existing items (and [CompletionFinished] event).
   *
   */
  @Serializable
  class CompletionItemsFinished(
    override val requestId: RpcCompletionRequestId,
  ) : RpcCompletionResponseEvent {
    override fun debugToString(): String = "CompletionItemsFinished"
  }

  /**
   * This event is sent when the completion session registers a new advertisement.
   */
  @Serializable
  class Advertisement(
    override val requestId: RpcCompletionRequestId,
    val message: @NlsContexts.PopupAdvertisement String,
    val icon: IconId? = null,
  ) : RpcCompletionResponseEvent {
    override fun toString(): String = buildToString("Advertisement") {
      field("message", message)
      fieldWithNullDefault("icon", icon)
    }

    override fun debugToString(): String = "Advertisement"
  }

  @Serializable
  data class AddWatchedPrefix(
    override val requestId: RpcCompletionRequestId,
    val condition: RpcRestartPrefixCondition,
  ) : RpcCompletionResponseEvent {
    override fun toString(): String = buildToString("AddWatchedPrefix") {
      field("condition", condition)
    }

    override fun debugToString(): String = "AddWatchedPrefix"
  }

  /**
   * The last event of a completion request's stream. Exactly one is sent per request, always last, and it states
   * the terminal [reason] explicitly so consumers don't have to reverse-engineer the outcome (a cancelled or failed
   * request must not have its partial results reused).
   */
  @Serializable
  data class CompletionFinished(
    override val requestId: RpcCompletionRequestId,
    val reason: RpcCompletionFinishReason,
  ) : RpcCompletionResponseEvent {
    override fun toString(): String = buildToString("CompletionFinished") {
      field("requestId", requestId)
      field("reason", reason)
    }

    override fun debugToString(): String = "CompletionFinished(reason=$reason)"
  }

  @Serializable
  data class BackendSettings(
    override val requestId: RpcCompletionRequestId,
    val mayHaveCustomPreview: Boolean,
  ) : RpcCompletionResponseEvent {
    override fun debugToString(): String = "BackendSettings(mayHaveCustomPreview=$mayHaveCustomPreview)"

    override fun toString(): String = buildToString("BackendSettings") {
      field("mayHaveCustomPreview", mayHaveCustomPreview)
    }
  }
}

fun Logger.logRpcCompletionResponseEvent(event: RpcCompletionResponseEvent) {
  if (isTraceEnabled) {
    trace(event.toString())
  }
  else if (isDebugEnabled) {
    debug(event.debugToString())
  }
}

