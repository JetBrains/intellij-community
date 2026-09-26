// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModDisplayMessage].
 */
@Serializable
data class RpcDisplayMessage(
  val messageText: String,
  val kind: MessageKind,
) : RpcModCommand {
  @Serializable
  enum class MessageKind {
    INFORMATION, ERROR
  }
}
