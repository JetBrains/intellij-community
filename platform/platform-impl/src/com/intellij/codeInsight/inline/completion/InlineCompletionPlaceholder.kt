// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface InlineCompletionPlaceholder {

  val element: InlineCompletionElement

  object Empty : InlineCompletionPlaceholder {
    override val element: InlineCompletionElement = InlineCompletionElement("")
  }

  class Custom(override val element: InlineCompletionElement) : InlineCompletionPlaceholder
}
