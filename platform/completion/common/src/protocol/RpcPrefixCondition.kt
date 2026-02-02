// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.serialization.FrontendFriendlyPrefixConditionSerializer
import com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.platform.completion.common.ccLogger
import kotlinx.serialization.Serializable

/**
 * Represents a serializable string condition from [com.intellij.codeInsight.completion.CompletionProcessBase.addWatchedPrefix].
 *
 * Conditions are serialized using the extension point `com.intellij.completion.frontendFriendlyPrefixCondition`.
 * Common patterns like [StandardPatterns.string], string().longerThan(), string().endsWith(), etc. are supported
 * out of the box. Custom patterns can be added by registering implementations of [PrefixConditionDescriptor]
 * and [com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptorConverter].
 *
 * @see FrontendFriendlyPrefixConditionSerializer
 * @see PrefixConditionDescriptor
 */
@Serializable
sealed interface RpcPrefixCondition {
  /**
   * Unconditional true - matches any prefix. Used for [StandardPatterns.string] with no conditions.
   */
  @Serializable
  object AlwaysTrue : RpcPrefixCondition

  /**
   * A serialized condition using the extension point-based serialization.
   * The descriptor contains all information needed to recreate the original [ElementPattern].
   */
  @Serializable
  data class Serialized(
    @Serializable(with = FrontendFriendlyPrefixConditionSerializer::class)
    val descriptor: PrefixConditionDescriptor
  ) : RpcPrefixCondition {
    override fun toString(): String = buildToString("Serialized") {
      field("descriptor", descriptor)
    }
  }

  // Legacy support - kept for backward compatibility
  /**
   * Condition that yields true if the given string equals to [value].
   * @deprecated Use [Serialized] with [EqualToDescriptor] instead.
   */
  @Serializable
  class EqualsTo(val value: String) : RpcPrefixCondition {
    override fun toString(): String = buildToString("EqualsTo") {
      field("value", value)
    }
  }
}

/**
 * Converts an [ElementPattern]<String> to an [RpcPrefixCondition] for serialization.
 *
 * The conversion uses the extension point `com.intellij.completion.frontendFriendlyPrefixCondition`
 * to find a suitable serializer. If no serializer is found, falls back to [RpcPrefixCondition.AlwaysTrue]
 * with a warning.
 *
 * Supported patterns include:
 * - [StandardPatterns.string] (no conditions) → [RpcPrefixCondition.AlwaysTrue]
 * - string().longerThan(n), string().shorterThan(n), string().withLength(n)
 * - string().startsWith(s), string().endsWith(s), string().contains(s)
 * - string().matches(regex), string().oneOf(...), string().equalTo(s)
 * - [StandardPatterns.or], [StandardPatterns.not]
 */
fun ElementPattern<String>.toRpc(): RpcPrefixCondition {
  // Try to serialize via extension point
  val descriptor = FrontendFriendlyPrefixConditionSerializer.toDescriptor(this)
  if (descriptor != null) {
    return RpcPrefixCondition.Serialized(descriptor)
  }

  // Fallback for base string() pattern (should be handled by AlwaysTrueConditionConverter, but just in case)
  if (this == StandardPatterns.string()) {
    return RpcPrefixCondition.AlwaysTrue
  }

  ccLogger.warn("Unsupported prefix condition: $this", Throwable())
  return RpcPrefixCondition.AlwaysTrue
}

/**
 * Converts an [RpcPrefixCondition] back to an [ElementPattern]<String>.
 */
fun RpcPrefixCondition.fromRpc(): ElementPattern<String> {
  return when (this) {
    is RpcPrefixCondition.AlwaysTrue -> StandardPatterns.string()
    is RpcPrefixCondition.Serialized -> descriptor.recreatePattern()
    is RpcPrefixCondition.EqualsTo -> StandardPatterns.string().equalTo(value) // Legacy support
  }
}
