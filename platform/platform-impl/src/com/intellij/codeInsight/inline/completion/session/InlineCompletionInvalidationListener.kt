// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import java.util.*

internal interface InlineCompletionInvalidationListener : EventListener {
  fun onInvalidatedByEvent(event: InlineCompletionEvent)

  fun onInvalidatedByUnclassifiedDocumentChange()
}
