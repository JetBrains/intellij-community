// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import fleet.util.UID
import kotlinx.serialization.Serializable

/**
 * Represents a single completion request. Contains all the necessary information to start a completion on backend.
 */
@Serializable
data class RpcCompletionRequest(
  val projectId: ProjectId,
  val editorId: EditorId,
  val startingEditorVersion: Int,
  val completionType: CompletionType,
  val invocationCount: Int,
) {
  val id: UID = UID.random()
}


