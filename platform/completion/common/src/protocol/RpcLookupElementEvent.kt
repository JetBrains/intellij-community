// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable

/**
 * Represents an event about showing/hiding a completion lookup and choosing a current completion item in it.
 */
@Serializable
sealed interface RpcLookupElementEvent {
  @Serializable
  data class SelectedItem(
    val requestId: RpcCompletionRequestId,
    val arrangementId: RpcCompletionArrangementId,
    val itemId: RpcCompletionItemId?,
    val itemPattern: String,
    val prefixLength: Int,
  ) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("SelectedItem") {
      field("requestId", requestId)
      field("itemId", itemId)
    }
  }

  @Serializable
  data class Cancel(val projectId: ProjectId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("Cancel") {
      field("projectId", projectId)
    }
  }

  @Serializable
  data class Show(val projectId: ProjectId, val editor: EditorId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("Show") {
      field("projectId", projectId)
      field("editor", editor)
    }
  }
}

