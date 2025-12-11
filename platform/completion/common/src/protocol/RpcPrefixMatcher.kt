// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.serialization.FrontendFriendlyPrefixMatcherSerializer
import com.intellij.codeInsight.completion.serialization.PrefixMatcherDescriptor
import kotlinx.serialization.Serializable

/**
 * Serializes [PrefixMatcher]s installed to completion items.
 * [PrefixMatcherDescriptor] is used for serialization
 */
@Serializable
sealed interface RpcPrefixMatcher {
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

  /**
   * Represents a [PrefixMatcher] that is available on the frontend.
   */
  @Serializable
  data class Frontend(
    @Serializable(with = FrontendFriendlyPrefixMatcherSerializer::class)
    val descriptor: PrefixMatcherDescriptor,
  ) : RpcPrefixMatcher {
    override fun toString(): String = buildToString("Frontend") {
      field("descriptor", descriptor)
    }
  }
}

fun PrefixMatcher.toRpc(itemId: RpcCompletionItemId): RpcPrefixMatcher {
  val descriptor = FrontendFriendlyPrefixMatcherSerializer.toDescriptor(this)
  return when (descriptor != null) {
    true -> RpcPrefixMatcher.Frontend(descriptor)
    false -> RpcPrefixMatcher.Backend(id = itemId, prefix = this.prefix)
      .also { RpcCompletionStat.registerMatcher(this) }
  }
}
