// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.serialization.ExtensionPointSerializer
import com.intellij.codeInsight.serialization.ExtensionPointSerializerBean
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

// TODO IJPL-207762 mark experimental
@ApiStatus.Internal
object FrontendFriendlyPrefixMatcherSerializer : ExtensionPointSerializer<PrefixMatcher, PrefixMatcherDescriptor>(
  epName = ep_name,
  descriptorClass = PrefixMatcherDescriptor::class
)

private val ep_name = ExtensionPointName<ExtensionPointSerializerBean>("com.intellij.completion.frontendFriendlyPrefixMatcher")