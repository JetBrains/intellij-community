// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.serialization.FrontendFriendlyPrefixConditionSerializer
import com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptor
import com.intellij.codeInsight.completion.serialization.PrefixConditionDescriptorConverter
import com.intellij.patterns.*
import kotlinx.serialization.Serializable

// ============================================================================
// AlwaysTrue - matches StandardPatterns.string() with no conditions
// ============================================================================

class AlwaysTrueConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    // Only match the base string() pattern with no conditions
    return if (target == StandardPatterns.string()) AlwaysTrueDescriptor else null
  }
}

@Serializable
object AlwaysTrueDescriptor : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string()
}

// ============================================================================
// LongerThan - string().longerThan(n)
// ============================================================================

class LongerThanConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.LongerThanCondition ?: return null
    return LongerThanDescriptor(condition.minLength)
  }
}

@Serializable
data class LongerThanDescriptor(val minLength: Int) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().longerThan(minLength)
}

// ============================================================================
// ShorterThan - string().shorterThan(n)
// ============================================================================

class ShorterThanConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.ShorterThanCondition ?: return null
    return ShorterThanDescriptor(condition.maxLength)
  }
}

@Serializable
data class ShorterThanDescriptor(val maxLength: Int) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().shorterThan(maxLength)
}

// ============================================================================
// WithLength - string().withLength(n)
// ============================================================================

class WithLengthConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.WithLengthCondition ?: return null
    return WithLengthDescriptor(condition.length)
  }
}

@Serializable
data class WithLengthDescriptor(val length: Int) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().withLength(length)
}

// ============================================================================
// StartsWith - string().startsWith(s)
// ============================================================================

class StartsWithConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.StartsWithCondition ?: return null
    return StartsWithDescriptor(condition.prefix)
  }
}

@Serializable
data class StartsWithDescriptor(val prefix: String) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().startsWith(prefix)
}

// ============================================================================
// EndsWith - string().endsWith(s)
// ============================================================================

class EndsWithConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.EndsWithCondition ?: return null
    return EndsWithDescriptor(condition.suffix)
  }
}

@Serializable
data class EndsWithDescriptor(val suffix: String) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().endsWith(suffix)
}

// ============================================================================
// Contains - string().contains(s)
// ============================================================================

class ContainsConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.ContainsCondition ?: return null
    return ContainsDescriptor(condition.substring)
  }
}

@Serializable
data class ContainsDescriptor(val substring: String) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().contains(substring)
}

// ============================================================================
// Matches - string().matches(regex)
// ============================================================================

class MatchesConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? StringPattern.MatchesCondition ?: return null
    return MatchesDescriptor(condition.regex)
  }
}

@Serializable
data class MatchesDescriptor(val regex: String) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().matches(regex)
}

// ============================================================================
// OneOf - string().oneOf(values)
// ============================================================================

class OneOfConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? ValuePatternCondition<*> ?: return null
    if (condition.debugMethodName != "oneOf") return null
    @Suppress("UNCHECKED_CAST")
    val values = (condition.values as? Collection<String>)?.toList() ?: return null
    return OneOfDescriptor(values)
  }
}

@Serializable
data class OneOfDescriptor(val values: List<String>) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().oneOf(values)
}

// ============================================================================
// OneOfIgnoreCase - string().oneOfIgnoreCase(values)
// ============================================================================

class OneOfIgnoreCaseConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? CaseInsensitiveValuePatternCondition ?: return null
    return OneOfIgnoreCaseDescriptor(condition.values.toList())
  }
}

@Serializable
data class OneOfIgnoreCaseDescriptor(val values: List<String>) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().oneOfIgnoreCase(*values.toTypedArray())
}

// ============================================================================
// Or - or(pattern1, pattern2, ...)
// ============================================================================

class OrPatternConverter : PrefixConditionDescriptorConverter<ElementPattern<String>> {
  override fun toDescriptor(target: ElementPattern<String>): PrefixConditionDescriptor? {
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
      FrontendFriendlyPrefixConditionSerializer.toDescriptor(it as ElementPattern<String>)
    }
    if (descriptors.size != patterns.size) return null

    return OrDescriptor(descriptors)
  }
}

@Serializable
data class OrDescriptor(val conditions: List<PrefixConditionDescriptor>) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> =
    StandardPatterns.or(*conditions.map { it.recreatePattern() }.toTypedArray())
}

// ============================================================================
// Not - not(pattern)
// ============================================================================

class NotPatternConverter : PrefixConditionDescriptorConverter<ElementPattern<String>> {
  override fun toDescriptor(target: ElementPattern<String>): PrefixConditionDescriptor? {
    val capture = target as? ObjectPattern.Capture<*> ?: return null
    val initial = capture.condition.initialCondition as? InitialPatternConditionPlus ?: return null
    val patterns = initial.patterns
    if (patterns.size != 1) return null

    // Check that this is a "not" pattern
    val debugStr = buildString { initial.append(this, "") }
    if (!debugStr.startsWith("not(")) return null

    @Suppress("UNCHECKED_CAST")
    val descriptor = FrontendFriendlyPrefixConditionSerializer.toDescriptor(patterns[0] as ElementPattern<String>)
      ?: return null

    return NotDescriptor(descriptor)
  }
}

@Serializable
data class NotDescriptor(val condition: PrefixConditionDescriptor) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> =
    StandardPatterns.not(condition.recreatePattern())
}

// ============================================================================
// EqualTo - string().equalTo(value)
// ============================================================================

class EqualToConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    val condition = target.condition.conditions.singleOrNull() as? ValuePatternCondition<*> ?: return null
    if (condition.debugMethodName != "equalTo") return null
    val value = condition.values.singleOrNull() as? String ?: return null
    return EqualToDescriptor(value)
  }
}

@Serializable
data class EqualToDescriptor(val value: String) : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().equalTo(value)
}

// ============================================================================
// EndsWithUppercaseLetter - string().endsWithUppercaseLetter()
// ============================================================================

class EndsWithUppercaseLetterConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    target.condition.conditions.singleOrNull() as? StringPattern.EndsWithUppercaseLetterCondition ?: return null
    return EndsWithUppercaseLetterDescriptor
  }
}

@Serializable
object EndsWithUppercaseLetterDescriptor : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().endsWithUppercaseLetter()
}

// ============================================================================
// AfterNonJavaIdentifierPart - string().afterNonJavaIdentifierPart()
// ============================================================================

class AfterNonJavaIdentifierPartConditionConverter : PrefixConditionDescriptorConverter<StringPattern> {
  override fun toDescriptor(target: StringPattern): PrefixConditionDescriptor? {
    target.condition.conditions.singleOrNull() as? StringPattern.AfterNonJavaIdentifierPartCondition ?: return null
    return AfterNonJavaIdentifierPartDescriptor
  }
}

@Serializable
object AfterNonJavaIdentifierPartDescriptor : PrefixConditionDescriptor {
  override fun recreatePattern(): ElementPattern<String> = StandardPatterns.string().afterNonJavaIdentifierPart()
}
