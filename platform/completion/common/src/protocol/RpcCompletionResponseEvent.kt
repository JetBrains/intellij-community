// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable

/**
 * Represents a single backend response event.
 */
@Serializable
sealed interface RpcCompletionResponseEvent {
  /**
   * This event is sent when new completion items become available.
   * Contains new items and the order for ALL items in the current completion session.
   */
  @Serializable
  data class NewItems(
    val newItems: List<RpcCompletionItem>,
    val completionListOrder: RpcCompletionListOrder,
  ) : RpcCompletionResponseEvent

  /**
   * This event is sent when all completion items are sent.
   * After it is sent, the backend can send only updates for the existing items (and [CompletionFinished] event).
   *
   */
  @Serializable
  object CompletionItemsFinished : RpcCompletionResponseEvent

  /**
   * This event is sent when the completion session registers a new advertisement.
   */
  @Serializable
  class Advertisement(
    val message: @NlsContexts.PopupAdvertisement String,
    val icon: IconId? = null,
  ) : RpcCompletionResponseEvent

  @Serializable
  data class AddWatchedPrefix(
    val offset: Int,
    val condition: RpcPrefixCondition,
  ) : RpcCompletionResponseEvent

  /**
   * The last event of the completion session.
   */
  @Serializable
  object CompletionFinished : RpcCompletionResponseEvent
}

