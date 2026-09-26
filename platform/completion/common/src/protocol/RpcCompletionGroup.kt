// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.group.CompletionGroup
import kotlinx.serialization.Serializable

@Serializable
data class RpcCompletionGroup(
  val order: Int,
  val displayName: String,
)

fun CompletionGroup.toRpc(): RpcCompletionGroup = RpcCompletionGroup(order(), displayName())

@Suppress("HardCodedStringLiteral")
fun RpcCompletionGroup.toCompletionGroup(): CompletionGroup = CompletionGroup(order, displayName)