// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.serialization.DescriptorConverter
import com.intellij.patterns.ElementPattern
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Descriptor interface for serializing `ElementPattern<String>` conditions used in
 * [com.intellij.codeInsight.completion.CompletionResultSet.restartCompletionOnPrefixChange].
 *
 * Implementations must be annotated with `@Serializable`
 * and registered via the `com.intellij.completion.frontendFriendlyPrefixCondition` extension point.
 *
 * @see FrontendFriendlyPrefixConditionSerializer
 * @see ElementPattern
 * @see com.intellij.patterns.StringPattern
 */
@ApiStatus.Internal //TODO IJPL-207762 mark experimental
@Serializable(with = FrontendFriendlyPrefixConditionSerializer::class)
interface PrefixConditionDescriptor {

  /** Recreates the original `ElementPattern<String>` from the serialized descriptor. */
  fun recreatePattern(): ElementPattern<String>
}

/**
 * Converter interface for transforming `ElementPattern<String>` instances into serializable descriptors.
 *
 * Implementations should be registered via the `com.intellij.completion.frontendFriendlyPrefixCondition`
 * extension point with the `converter` attribute.
 *
 * @see PrefixConditionDescriptor
 */
@ApiStatus.Internal //TODO IJPL-207762 mark experimental
interface PrefixConditionDescriptorConverter<Pattern : ElementPattern<String>> : DescriptorConverter<Pattern, PrefixConditionDescriptor>
