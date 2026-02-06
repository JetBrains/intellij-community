// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.serialization.FrontendFriendlyRestartPrefixConditionSerializer
import com.intellij.codeInsight.completion.serialization.RestartPrefixConditionDescriptor
import com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptorConverter
import com.intellij.patterns.CaseInsensitiveValuePatternCondition
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.InitialPatternConditionPlus
import com.intellij.patterns.ObjectPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PatternConditionPlus
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.StringPattern
import com.intellij.patterns.ValuePatternCondition
import kotlinx.serialization.Serializable

// ============================================================================
// AlwaysTrue - matches StandardPatterns.string() with no conditions
// ============================================================================

class AlwaysTrueConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    // Only match the base string() pattern with no conditions
    return if (target == StandardPatterns.string()) AlwaysTrueDescriptor else null
  }
}

@Serializable
object AlwaysTrueDescriptor : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string()
}

// ============================================================================
// LongerThan - string().longerThan(n)
// ============================================================================

class LongerThanConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.LongerThanCondition ?: return null
    return LongerThanDescriptor(condition.minLength)
  }
}

@Serializable
data class LongerThanDescriptor(val minLength: Int) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().longerThan(minLength)
}

// ============================================================================
// ShorterThan - string().shorterThan(n)
// ============================================================================

class ShorterThanConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.ShorterThanCondition ?: return null
    return ShorterThanDescriptor(condition.maxLength)
  }
}

@Serializable
data class ShorterThanDescriptor(val maxLength: Int) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().shorterThan(maxLength)
}

// ============================================================================
// WithLength - string().withLength(n)
// ============================================================================

class WithLengthConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.WithLengthCondition ?: return null
    return WithLengthDescriptor(condition.length)
  }
}

@Serializable
data class WithLengthDescriptor(val length: Int) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().withLength(length)
}

// ============================================================================
// StartsWith - string().startsWith(s)
// ============================================================================

class StartsWithConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.StartsWithCondition ?: return null
    return StartsWithDescriptor(condition.prefix)
  }
}

@Serializable
data class StartsWithDescriptor(val prefix: String) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().startsWith(prefix)
}

// ============================================================================
// EndsWith - string().endsWith(s)
// ============================================================================

class EndsWithConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.EndsWithCondition ?: return null
    return EndsWithDescriptor(condition.suffix)
  }
}

@Serializable
data class EndsWithDescriptor(val suffix: String) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().endsWith(suffix)
}

// ============================================================================
// Contains - string().contains(s)
// ============================================================================

class ContainsConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.ContainsCondition ?: return null
    return ContainsDescriptor(condition.substring)
  }
}

@Serializable
data class ContainsDescriptor(val substring: String) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().contains(substring)
}

// ============================================================================
// Matches - string().matches(regex)
// ============================================================================

class MatchesConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.MatchesCondition ?: return null
    return MatchesDescriptor(condition.regex)
  }
}

@Serializable
data class MatchesDescriptor(val regex: String) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().matches(regex)
}

// ============================================================================
// OneOf - string().oneOf(values)
// ============================================================================

class OneOfConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? ValuePatternCondition<*> ?: return null
    if (condition.debugMethodName != "oneOf") return null
    @Suppress("UNCHECKED_CAST")
    val values = (condition.values as? Collection<String>)?.toList() ?: return null
    return OneOfDescriptor(values)
  }
}

@Serializable
data class OneOfDescriptor(val values: List<String>) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().oneOf(values)
}

// ============================================================================
// OneOfIgnoreCase - string().oneOfIgnoreCase(values)
// ============================================================================

class OneOfIgnoreCaseConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? CaseInsensitiveValuePatternCondition ?: return null
    return OneOfIgnoreCaseDescriptor(condition.values.toList())
  }
}

@Serializable
data class OneOfIgnoreCaseDescriptor(val values: List<String>) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().oneOfIgnoreCase(*values.toTypedArray())
}

// ============================================================================
// Or - or(pattern1, pattern2, ...)
// ============================================================================

class OrPatternConverter : PrefixConditionDescriptorConverter<ElementPattern<String>> {
  override fun toDescriptor(target: ElementPattern<String>): RestartPrefixConditionDescriptor? {
    val capture = target as? ObjectPattern.Capture<*> ?: return null
    val initial = capture.condition.initialCondition as? InitialPatternConditionPlus ?: return null
    val patterns = initial.patterns
    if (patterns.size < 2) return null

    // Check that this is an "or" pattern by checking the debug string
    val debugStr = buildString { initial.append(this, "") }
    // "or" patterns don't have a specific prefix, but "not" patterns do
    if (debugStr.startsWith("not(")) return null

    val descriptors = patterns.mapNotNull {
      @Suppress("UNCHECKED_CAST")
      FrontendFriendlyRestartPrefixConditionSerializer.toDescriptor(it as ElementPattern<String>)
    }
    if (descriptors.size != patterns.size) return null

    return OrDescriptor(descriptors)
  }
}

@Serializable
data class OrDescriptor(val conditions: List<RestartPrefixConditionDescriptor>) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> =
    StandardPatterns.or(*conditions.map { it.recreatePattern() }.toTypedArray())
}

// ============================================================================
// Not - not(pattern)
// ============================================================================

class NotPatternConverter : PrefixConditionDescriptorConverter<ElementPattern<String>> {
  override fun toDescriptor(target: ElementPattern<String>): RestartPrefixConditionDescriptor? {
    val capture = target as? ObjectPattern.Capture<*> ?: return null
    val initial = capture.condition.initialCondition as? InitialPatternConditionPlus ?: return null
    val patterns = initial.patterns
    if (patterns.size != 1) return null

    // Check that this is a "not" pattern
    val debugStr = buildString { initial.append(this, "") }
    if (!debugStr.startsWith("not(")) return null

    @Suppress("UNCHECKED_CAST")
    val descriptor = FrontendFriendlyRestartPrefixConditionSerializer.toDescriptor(patterns[0] as ElementPattern<String>)
                     ?: return null

    return NotDescriptor(descriptor)
  }
}

@Serializable
data class NotDescriptor(val condition: RestartPrefixConditionDescriptor) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> =
    StandardPatterns.not(condition.recreatePattern())
}

// ============================================================================
// EqualTo - string().equalTo(value)
// ============================================================================

class EqualToConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? ValuePatternCondition<*> ?: return null
    if (condition.debugMethodName != "equalTo") return null
    val value = condition.values.singleOrNull() as? String ?: return null
    return EqualToDescriptor(value)
  }
}

@Serializable
data class EqualToDescriptor(val value: String) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().equalTo(value)
}

// ============================================================================
// EndsWithUppercaseLetter - string().endsWithUppercaseLetter()
// ============================================================================

class EndsWithUppercaseLetterConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    target.condition.conditions.singleOrNull() as? StringPattern.EndsWithUppercaseLetterCondition ?: return null
    return EndsWithUppercaseLetterDescriptor
  }
}

@Serializable
object EndsWithUppercaseLetterDescriptor : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().endsWithUppercaseLetter()
}

// ============================================================================
// AfterNonJavaIdentifierPart - string().afterNonJavaIdentifierPart()
// ============================================================================

class AfterNonJavaIdentifierPartConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    target.condition.conditions.singleOrNull() as? StringPattern.AfterNonJavaIdentifierPartCondition ?: return null
    return AfterNonJavaIdentifierPartDescriptor
  }
}

@Serializable
object AfterNonJavaIdentifierPartDescriptor : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().afterNonJavaIdentifierPart()
}

// ============================================================================
// And - pattern.and(otherPattern)
// ============================================================================

class AndPatternConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): RestartPrefixConditionDescriptor? {
    val conditions = target.condition.conditions
    if (conditions.isEmpty()) return null

    // Find the last condition that is a PatternConditionPlus with "and"
    val lastCondition = conditions.lastOrNull()
    val andCondition = lastCondition as? PatternConditionPlus<*, *> ?: return null
    if (andCondition.debugMethodName != "and") return null

    // Get the wrapped pattern from and()
    @Suppress("UNCHECKED_CAST")
    val wrappedPattern = andCondition.valuePattern as? ElementPattern<String> ?: return null
    val wrappedDescriptor = FrontendFriendlyRestartPrefixConditionSerializer.toDescriptor(wrappedPattern)
                            ?: return null

    // Get base pattern conditions (without the and)
    val baseConditions = conditions.dropLast(1)
    val baseDescriptor = when {
      baseConditions.isEmpty() -> AlwaysTrueDescriptor
      baseConditions.size == 1 -> {
        // Try to match the single condition to a known descriptor
        getDescriptorForSingleCondition(baseConditions.single()) ?: return null
      }
      else -> return null // TODO Multiple conditions not yet supported
    }

    return AndDescriptor(baseDescriptor, wrappedDescriptor)
  }

  // TODO rework, this is a temporary solution until we have a proper way to handle multiple conditions
  //      it should support arbitrary conditions
  private fun getDescriptorForSingleCondition(condition: PatternCondition<*>): RestartPrefixConditionDescriptor? {
    return when (condition) {
      is StringPattern.LongerThanCondition -> LongerThanDescriptor(condition.minLength)
      is StringPattern.ShorterThanCondition -> ShorterThanDescriptor(condition.maxLength)
      is StringPattern.WithLengthCondition -> WithLengthDescriptor(condition.length)
      is StringPattern.StartsWithCondition -> StartsWithDescriptor(condition.prefix)
      is StringPattern.EndsWithCondition -> EndsWithDescriptor(condition.suffix)
      is StringPattern.ContainsCondition -> ContainsDescriptor(condition.substring)
      is StringPattern.EndsWithUppercaseLetterCondition -> EndsWithUppercaseLetterDescriptor
      is StringPattern.AfterNonJavaIdentifierPartCondition -> AfterNonJavaIdentifierPartDescriptor
      else -> null
    }
  }
}

@Serializable
data class AndDescriptor(
  val base: RestartPrefixConditionDescriptor,
  val combined: RestartPrefixConditionDescriptor
) : RestartPrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> {
    val basePattern = base.recreatePattern()
    val combinedPattern = combined.recreatePattern()
    @Suppress("UNCHECKED_CAST")
    return (basePattern as ObjectPattern<String, *>).and(combinedPattern)
  }
}
