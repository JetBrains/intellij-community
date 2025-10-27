// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.platform.completion.common.ccLogger
import kotlinx.serialization.Serializable

/**
 * Represents a string condition from [com.intellij.codeInsight.completion.CompletionProcessBase.addWatchedPrefix]
 */
@Serializable
sealed interface RpcPrefixCondition {
  /** unconditional true */
  @Serializable
  object AlwaysTrue : RpcPrefixCondition

  /** condition that yields true if the given string equals to [value] */
  @Serializable
  class EqualsTo(val value: String) : RpcPrefixCondition
}

fun ElementPattern<String>.toRpc(): RpcPrefixCondition {
  if (this == StandardPatterns.string()) {
    return RpcPrefixCondition.AlwaysTrue
  }

  ccLogger.warn("Unsupported condition: $this", Throwable())

  // todo there is no way to serialize a pattern yet
  return RpcPrefixCondition.AlwaysTrue
}