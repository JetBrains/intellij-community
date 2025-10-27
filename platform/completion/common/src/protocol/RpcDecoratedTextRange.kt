// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementPresentation.DecoratedTextRange
import com.intellij.ide.rpc.util.TextRangeId
import com.intellij.ide.rpc.util.toRpc
import kotlinx.serialization.Serializable

@Serializable
data class RpcDecoratedTextRange(
  val textRange: TextRangeId,
  val decoration: LookupElementPresentation.LookupItemDecoration,
)

fun DecoratedTextRange.toRpc(): RpcDecoratedTextRange {
  return RpcDecoratedTextRange(
    textRange = textRange.toRpc(),
    decoration = decoration,
  )
}
