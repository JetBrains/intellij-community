// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.render.InlineCompletionBlock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface InlineCompletionPlaceholder {

  val element: InlineCompletionBlock

  object Empty : InlineCompletionPlaceholder {
    override val element: InlineCompletionBlock = InlineCompletionGrayTextElement("")
  }

  class Custom(override val element: InlineCompletionBlock) : InlineCompletionPlaceholder
}
