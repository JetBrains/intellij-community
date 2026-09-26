// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.serialization

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.serialization.DescriptorConverter
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

// TODO IJPL-207762 mark experimental
@ApiStatus.Internal
@Serializable(with = FrontendFriendlyPrefixMatcherSerializer::class)
interface PrefixMatcherDescriptor {
  fun recreateMatcher(): PrefixMatcher
}

// TODO IJPL-207762 mark experimental
@ApiStatus.Internal
interface PrefixMatcherDescriptorConverter<Matcher : PrefixMatcher> : DescriptorConverter<Matcher, PrefixMatcherDescriptor>
