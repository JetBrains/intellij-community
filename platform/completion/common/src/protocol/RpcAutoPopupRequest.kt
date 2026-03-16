// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.editor.impl.EditorId
import kotlinx.serialization.Serializable

@Serializable
data class RpcAutoPopupRequest(
  val editorId: EditorId,
  val completionType: CompletionType,
)
