// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import com.intellij.platform.completion.common.protocol.RpcCompletionItemId
import kotlinx.serialization.Serializable

/**
 * Result of ModCommand computation for a completion item.
 */
@Serializable
data class RpcModCommandResult(
  val completionItemId: RpcCompletionItemId,
  val modCommand: RpcModCommand,
)
