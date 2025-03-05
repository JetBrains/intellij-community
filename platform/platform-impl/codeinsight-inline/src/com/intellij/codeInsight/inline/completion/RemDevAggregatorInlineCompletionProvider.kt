// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

/**
 * **This interface must have exactly once instance, and it's only on the client when the RemDev mode is active**.
 *
 * Its purpose is to proxy Inline Completion providers from the Host side.
 *
 * Since it repeats all the actions from the Host side, this provider skips all the logs.
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface RemDevAggregatorInlineCompletionProvider : InlineCompletionProvider {
  val currentProviderId: InlineCompletionProviderID?
}
