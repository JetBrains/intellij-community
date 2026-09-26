// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.StartOnlyMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.completion.serialization.FrontendFriendlyPrefixMatcherSerializer
import com.intellij.codeInsight.completion.serialization.PrefixMatcherDescriptor
import com.intellij.codeInsight.completion.serialization.PrefixMatcherDescriptorConverter
import kotlinx.serialization.Serializable

internal class PlainPrefixMatcherDescriptorConverter : PrefixMatcherDescriptorConverter<PlainPrefixMatcher> {
  override fun toDescriptor(target: PlainPrefixMatcher): PlainPrefixMatcherDescriptor =
    PlainPrefixMatcherDescriptor(target.prefix, target.isStartMatchOnly)
}

@Serializable
internal data class PlainPrefixMatcherDescriptor(
  val prefix: String,
  val isStartMatchOnly: Boolean,
) : PrefixMatcherDescriptor {
  override fun recreateMatcher(): PrefixMatcher = PlainPrefixMatcher(prefix, isStartMatchOnly)
}

internal class CamelHumpMatcherDescriptorConverter : PrefixMatcherDescriptorConverter<CamelHumpMatcher> {
  override fun toDescriptor(target: CamelHumpMatcher): CamelHumpMatcherDescriptor =
    CamelHumpMatcherDescriptor(target.prefix, target.isCaseSensitive, target.isTypoTolerant)
}

@Serializable
internal data class CamelHumpMatcherDescriptor(
  val prefix: String,
  val caseSensitive: Boolean,
  val typoTolerant: Boolean,
) : PrefixMatcherDescriptor {
  override fun recreateMatcher(): PrefixMatcher = CamelHumpMatcher(prefix, caseSensitive, typoTolerant)
}

internal class StartOnlyMatcherDescriptorConverter : PrefixMatcherDescriptorConverter<StartOnlyMatcher> {
  override fun toDescriptor(target: StartOnlyMatcher): PrefixMatcherDescriptor? {
    val delegate = FrontendFriendlyPrefixMatcherSerializer.toDescriptor(target.delegate) ?: return null
    return StartOnlyMatcherDescriptor(delegate)
  }
}

@Serializable
internal data class StartOnlyMatcherDescriptor(
  val delegate: PrefixMatcherDescriptor,
) : PrefixMatcherDescriptor {
  override fun recreateMatcher(): PrefixMatcher = StartOnlyMatcher(delegate.recreateMatcher())
}