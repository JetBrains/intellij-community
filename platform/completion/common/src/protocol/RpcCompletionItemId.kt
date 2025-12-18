// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import fleet.util.UID
import kotlinx.serialization.Serializable

/**
 * Unique identifier of a completion item.
 */
@Serializable
data class RpcCompletionItemId(
  val id: UID = UID.random(),
) {
  override fun toString(): String = "RpcId($id)"
}
