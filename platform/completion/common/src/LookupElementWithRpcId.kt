// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.completion.common.protocol.RpcCompletionItem
import com.intellij.platform.completion.common.protocol.RpcCompletionItemId

/**
 * Marker interface for [LookupElement]s that are backed by [RpcCompletionItem]s.
 */
interface LookupElementWithRpcId {
  /**
   * ID of the corresponding [RpcCompletionItem] or `null` if the element is not backed by an RPC completion item.
   */
  val rpcId: RpcCompletionItemId?
}