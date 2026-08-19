// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable

/**
 * Represents an event about showing/hiding a completion lookup and choosing a current completion item in it.
 */
@Serializable
sealed interface RpcLookupElementEvent {
  /**
   * The lookup state changed. Only changed properties are included (null means not changed).
   */
  @Serializable
  data class LookupStateChanged(
    val requestId: RpcCompletionRequestId,
    val selectedItemId: RpcSelectedItem?,
    val focusDegree: LookupFocusDegree? = null,
    val sortedItemIds: List<RpcCompletionItemId>? = null,
  ) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("LookupStateChanged") {
      field("requestId", requestId)
      fieldWithNullDefault("selectedItemId", selectedItemId)
      fieldWithNullDefault("focusDegree", focusDegree)
      fieldWithNullDefault("sortedItemIds", sortedItemIds)
    }
  }

  /**
   * the lookup is closed without completion
   */
  @Serializable
  data class Cancel(val projectId: ProjectId, val requestId: RpcCompletionRequestId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("Cancel") {
      field("projectId", projectId)
      field("requestId", requestId)
    }
  }

  /**
   * the lookup is closed with completion
   *
   * [requestId] is the request the chosen item belongs to, and it is what the backend must resolve
   * [selectedItemId] against: items live in a per-request storage
   * (`BackendCompletionRequestSession.findCompletionResult`), and the mirror lookup's arranger can still belong to an
   * older request when this event is handled (IJPL-252099 — the frontend swaps a stale-seeded request for a fresh one
   * right before the accept). Resolving against the arranger's own session instead would fail to find the item and
   * insert one carrying a shorter prefix matcher, leaving the leading typed characters in the document.
   *
   * [itemPattern] is the prefix the *frontend* lookup matched the chosen element against
   * (`Lookup.itemPattern`), and it is the authoritative measure of the range the insertion must replace: the frontend
   * owns the lookup, so it owns the lookup start offset. It can be **longer** than the matcher the backend stored for
   * the item — a stale-seeded placeholder shows an older request's items re-matched against the prefix typed since
   * (`FrontendCompletionRequestSessionImpl.seedStaleAndSwap`), and accepting one before the swap-restart lands would
   * otherwise measure the replaced range against the older, shorter matcher and duplicate the leading typed characters
   * (IJPL-252099). `null` means "no pattern reported" (nothing chosen, or an older frontend), in which case the backend
   * keeps using the item's own matcher.
   */
  @Serializable
  data class ItemSelected(
    val projectId: ProjectId,
    val requestId: RpcCompletionRequestId,
    val selectedItemId: RpcCompletionItemId? = null,
    val itemPattern: String? = null,
  ) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("ItemSelected") {
      field("projectId", projectId)
      field("requestId", requestId)
      fieldWithNullDefault("selectedItemId", selectedItemId)
      fieldWithNullDefault("itemPattern", itemPattern)
    }
  }

}


@Serializable
data class RpcSelectedItem(val value: RpcCompletionItemId? = null) {
  override fun toString(): String = buildToString("RpcSelectedItem") {
    fieldWithNullDefault("value", value)
  }
}

fun Logger.logLookupElementEvent(event: RpcLookupElementEvent) {
  if (isTraceEnabled) {
    trace(event.toString())
  }
  else if (isDebugEnabled) {
    when (event) {
      is RpcLookupElementEvent.Cancel -> debug("Lookup cancelled by client")
      is RpcLookupElementEvent.ItemSelected -> debug("Lookup item selected")
      is RpcLookupElementEvent.LookupStateChanged -> debug("Lookup state changed: ${event.selectedItemId} ${event.focusDegree} ${event.sortedItemIds?.size}")
    }
  }
}
