// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.completion.FrontendFriendlyInsertHandler
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.serialization.ExtensionPointSerializer
import com.intellij.codeInsight.serialization.ExtensionPointSerializerBean
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Serializer for [InsertHandler].
 *
 * It uses [ep_name] to collect serializers for all known [InsertHandlerSerializer] implementations.
 */
@ApiStatus.Internal
object InsertHandlerSerializer : ExtensionPointSerializer<InsertHandler<*>, FrontendFriendlyInsertHandler>(
  epName = ep_name,
  descriptorClass = FrontendFriendlyInsertHandler::class
)

private val ep_name = ExtensionPointName<ExtensionPointSerializerBean>("com.intellij.completion.frontendFriendlyInsertHandler")