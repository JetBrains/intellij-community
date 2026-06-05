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
  val editorId: EditorId,
  val projectId: ProjectId,
  val startingEditorVersion: Int,
  val completionType: CompletionType = CompletionType.BASIC,
  val invocationCount: Int = 0,
) {
  override fun toString(): String = buildToString("RpcCompletionRequest") {
    field("id", id)
    field("editorId", editorId)
    field("projectId", projectId)
    field("startingEditorVersion", startingEditorVersion)
    fieldWithDefault("completionType", completionType, CompletionType.BASIC)
    fieldWithDefault("invocationCount", invocationCount, 0)
  }
}