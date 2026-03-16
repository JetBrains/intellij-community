// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupFocusDegree
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
    val selectedItemId: RpcSelectedItem? = null,
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
  data class Cancel(val projectId: ProjectId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("Cancel") {
      field("projectId", projectId)
    }
  }

  /**
   * the lookup is closed with completion
   */
  @Serializable
  data class ItemSelected(val projectId: ProjectId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("ItemSelected") {
      field("projectId", projectId)
    }
  }

}


@Serializable
data class RpcSelectedItem(val value: RpcCompletionItemId? = null) {
  override fun toString(): String = buildToString("RpcSelectedItem") {
    fieldWithNullDefault("value", value)
  }
}
