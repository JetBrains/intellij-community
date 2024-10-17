// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

// TODO docs
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface RemDevAggregatorInlineCompletionProvider : InlineCompletionProvider {
  val currentProviderId: InlineCompletionProviderID?
}
