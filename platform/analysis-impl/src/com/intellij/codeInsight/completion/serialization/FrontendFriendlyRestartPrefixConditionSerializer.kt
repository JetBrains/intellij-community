// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.serialization.ExtensionPointSerializer
import com.intellij.codeInsight.serialization.ExtensionPointSerializerBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.patterns.ElementPattern
import org.jetbrains.annotations.ApiStatus

private val EP_NAME = ExtensionPointName<ExtensionPointSerializerBean>(
  "com.intellij.completion.frontendFriendlyRestartPrefixCondition"
)

/**
 * Serializer for [ElementPattern]<String> conditions used in prefix restart logic.
 *
 * Uses the extension point `com.intellij.completion.frontendFriendlyPrefixCondition` to
 * dispatch serialization to the appropriate [RestartPrefixConditionDescriptor] implementation.
 *
 * @see RestartPrefixConditionDescriptor
 * @see PrefixConditionDescriptorConverter
 */
@ApiStatus.Internal //TODO IJPL-207762 mark experimental
object FrontendFriendlyRestartPrefixConditionSerializer : ExtensionPointSerializer<ElementPattern<String>, RestartPrefixConditionDescriptor>(
  EP_NAME,
  RestartPrefixConditionDescriptor::class
)
