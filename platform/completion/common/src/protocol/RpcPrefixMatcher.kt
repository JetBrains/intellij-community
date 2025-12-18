// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import kotlinx.serialization.Serializable

/**
 * Serializes [PrefixMatcher]s installed to completion items.
 */
@Serializable
sealed interface RpcPrefixMatcher {
  /** Represents [com.intellij.codeInsight.completion.impl.CamelHumpMatcher] */
  @Serializable
  data class CamelHumpMatcher(val prefix: String, val caseSensitive: Boolean, val typoTolerant: Boolean) : RpcPrefixMatcher {
    override fun toString(): String = buildToString("CamelHumpMatcher") {
      field("prefix", prefix)
      field("caseSensitive", caseSensitive)
      field("typoTolerant", typoTolerant)
    }
  }


  /**
   * Represents a [PrefixMatcher] that is not available on the frontend.
   */
  @Serializable
  data class Backend(val id: RpcCompletionItemId, val prefix: String) : RpcPrefixMatcher {
    override fun toString(): String = buildToString("Backend") {
      field("id", id)
      field("prefix", prefix)
    }
  }

}

fun PrefixMatcher.toRpc(itemId: RpcCompletionItemId): RpcPrefixMatcher {
  if (this.javaClass == CamelHumpMatcher::class.java) {
    this as CamelHumpMatcher
    return RpcPrefixMatcher.CamelHumpMatcher(this.prefix, this.isCaseSensitive, this.isTypoTolerant)
      .also { RpcCompletionStat.registerMatcher(this) }
  }

  return RpcPrefixMatcher.Backend(id = itemId, prefix = this.prefix)
}
