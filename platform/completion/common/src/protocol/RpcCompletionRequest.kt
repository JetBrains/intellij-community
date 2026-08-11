// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable

/**
 * Represents a single completion request. Contains all the necessary information to start a completion on backend.
 */
@Serializable
data class RpcCompletionRequest(
  val id: RpcCompletionRequestId,
  val sessionId: RpcCompletionSessionId,
  val editorId: EditorId,
  val projectId: ProjectId,
  val startingEditorVersion: Int,
  val startingDocumentVersion: Int,
  val completionType: CompletionType = CompletionType.BASIC,
  val invocationCount: Int = 0,
  /**
   * True for a request the backend created to restart completion after a write action (see
   * `BackendCompletionSession.restartAfterWriteAction`). Used to break supersession ties deterministically: at equal
   * editor/document versions a frontend-issued request always wins over a write-action restart, so the two processes
   * agree on the surviving request regardless of which one registered first. Always false for frontend-issued requests.
   */
  val isWriteActionRestart: Boolean = false,
) {
  override fun toString(): String = buildToString("RpcCompletionRequest") {
    field("id", id)
    field("sessionId", sessionId)
    field("editorId", editorId)
    field("projectId", projectId)
    field("startingEditorVersion", startingEditorVersion)
    field("startingDocumentVersion", startingDocumentVersion)
    fieldWithDefault("completionType", completionType, CompletionType.BASIC)
    fieldWithDefault("invocationCount", invocationCount, 0)
    fieldWithDefault("isWriteActionRestart", isWriteActionRestart, false)
  }
}