// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.serialization.FrontendFriendlyRestartPrefixConditionSerializer
import com.intellij.codeInsight.completion.serialization.RestartPrefixConditionDescriptor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.platform.completion.common.ccLogger
import kotlinx.serialization.Serializable

/**
 * Represents a serializable string condition from [com.intellij.codeInsight.completion.CompletionProcessBase.addWatchedPrefix].
 *
 * Conditions are serialized using the extension point `com.intellij.completion.frontendFriendlyPrefixCondition`.
 * Common patterns like [StandardPatterns.string], string().longerThan(), string().endsWith(), etc. are supported
 * out of the box. Custom patterns can be added by registering implementations of [RestartPrefixConditionDescriptor]
 * and [com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptorConverter].
 *
 * @see FrontendFriendlyRestartPrefixConditionSerializer
 * @see RestartPrefixConditionDescriptor
 */
@Serializable
sealed interface RpcRestartPrefixCondition {
  /**
   * Unconditional true - matches any prefix. Used for [StandardPatterns.string] with no conditions.
   */
  @Serializable
  object AlwaysTrue : RpcRestartPrefixCondition

  /**
   * A serialized condition using the extension point-based serialization.
   * The descriptor contains all information needed to recreate the original [ElementPattern].
   */
  @Serializable
  data class Serialized(
    @Serializable(with = FrontendFriendlyRestartPrefixConditionSerializer::class)
    val descriptor: RestartPrefixConditionDescriptor
  ) : RpcRestartPrefixCondition {
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
  class EqualsTo(val value: String) : RpcRestartPrefixCondition {
    override fun toString(): String = buildToString("EqualsTo") {
      field("value", value)
    }
  }
}

/**
 * Converts an [ElementPattern]<String> to an [RpcRestartPrefixCondition] for serialization.
 *
 * The conversion uses the extension point `com.intellij.completion.frontendFriendlyPrefixCondition`
 * to find a suitable serializer. If no serializer is found, falls back to [RpcRestartPrefixCondition.AlwaysTrue]
 * with a warning.
 *
 * Supported patterns include:
 * - [StandardPatterns.string] (no conditions) → [RpcRestartPrefixCondition.AlwaysTrue]
 * - string().longerThan(n), string().shorterThan(n), string().withLength(n)
 * - string().startsWith(s), string().endsWith(s), string().contains(s)
 * - string().matches(regex), string().oneOf(...), string().equalTo(s)
 * - [StandardPatterns.or], [StandardPatterns.not]
 */
fun ElementPattern<String>.toRpc(): RpcRestartPrefixCondition {
  // Try to serialize via extension point
  val descriptor = FrontendFriendlyRestartPrefixConditionSerializer.toDescriptor(this)
  if (descriptor != null) {
    return RpcRestartPrefixCondition.Serialized(descriptor)
  }

  // Fallback for base string() pattern (should be handled by AlwaysTrueConditionConverter, but just in case)
  if (this == StandardPatterns.string()) {
    return RpcRestartPrefixCondition.AlwaysTrue
  }

  ccLogger.warn("Unsupported prefix condition: $this", Throwable())
  return RpcRestartPrefixCondition.AlwaysTrue
}

/**
 * Converts an [RpcRestartPrefixCondition] back to an [ElementPattern]<String>.
 */
fun RpcRestartPrefixCondition.fromRpc(): ElementPattern<String> {
  return when (this) {
    is RpcRestartPrefixCondition.AlwaysTrue -> StandardPatterns.string()
    is RpcRestartPrefixCondition.Serialized -> descriptor.recreatePattern()
    is RpcRestartPrefixCondition.EqualsTo -> StandardPatterns.string().equalTo(value) // Legacy support
  }
}
