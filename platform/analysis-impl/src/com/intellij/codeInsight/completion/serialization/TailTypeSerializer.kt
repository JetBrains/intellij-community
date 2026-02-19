// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.FrontendFriendlyTailType
import com.intellij.codeInsight.serialization.ExtensionPointSerializer
import com.intellij.codeInsight.serialization.ExtensionPointSerializerBean
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Serializer for [TailType].
 *
 * It uses [EP_NAME] to collect serializers for all known [FrontendFriendlyTailType] implementations.
 *
 * @see InsertHandlerSerializer
 */
@ApiStatus.Internal
object TailTypeSerializer : ExtensionPointSerializer<TailType, FrontendFriendlyTailType>(
  epName = EP_NAME,
  descriptorClass = FrontendFriendlyTailType::class
)

private val EP_NAME = ExtensionPointName<ExtensionPointSerializerBean>("com.intellij.completion.frontendFriendlyTailType")
