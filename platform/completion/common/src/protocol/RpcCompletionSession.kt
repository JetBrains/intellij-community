// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionSession(
  val id: RpcCompletionSessionId,
) {
  override fun toString(): String = buildToString("RpcCompletionSession") {
    field("id", id)
  }
}
