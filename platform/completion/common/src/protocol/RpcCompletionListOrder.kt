// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * Contains weights of ALL completion items reported by the current backend completion session.
 */
@Serializable
data class RpcCompletionListOrder(
  val weights: List<RpcCompletionItemWeight>,
)

