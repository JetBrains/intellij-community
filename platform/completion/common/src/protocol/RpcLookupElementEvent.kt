// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    val request: RpcCompletionRequest,
    val itemId: RpcCompletionItemId,
  ) : RpcLookupElementEvent

  @Serializable
  data class Cancel(val projectId: ProjectId) : RpcLookupElementEvent

  @Serializable
  class Show(val projectId: ProjectId, val editor: EditorId) : RpcLookupElementEvent
}