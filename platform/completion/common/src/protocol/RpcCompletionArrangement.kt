// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * Contains weights of *matched* completion items reported by the current backend completion session.
 * All other items are considered *non-matched*, and their weights are not reported.
 * Non-matched items are passed as well.
 *
 * Id represents the version of the arrangement. Backend stores several last arrangements.
 */
@Serializable
data class RpcCompletionArrangement(
  val id: RpcCompletionArrangementId = RpcCompletionArrangementId(),
  val weightsOfMatchedItems: List<RpcCompletionItemWeight> = emptyList(),
  val nonMatchedItems: List<RpcCompletionItemId> = emptyList(),
  val startMatches: List<RpcCompletionItemId> = emptyList(),
) {
  override fun toString(): String = buildToString("RpcCompletionArrangement") {
    fieldWithEmptyDefault("weightsOfMatchedItems", weightsOfMatchedItems)
    fieldWithEmptyDefault("nonMatchedItems", nonMatchedItems)
    fieldWithEmptyDefault("startMatches", startMatches)
  }
}
