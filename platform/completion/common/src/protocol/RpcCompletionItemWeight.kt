// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * Contains [weight] of a completion item with [id].
 * The weight is used to sort completion items.
 * The lower the weight, the higher the priority.
 * Zero is the highest priority.
 */
@Serializable
data class RpcCompletionItemWeight(
  val id: RpcCompletionItemId,
  val weight: Int,
) {
  override fun toString(): String = buildToString("RpcCompletionItemWeight") {
    field("id", id)
    field("weight", weight)
  }
}