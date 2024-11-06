// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * If a request is applicable corresponding to [isApplicable], then everything works as usual.
 *
 * If a request is not applicable, the listener doesn't call any methods on all the events connected to the request.
 */
internal abstract class InlineCompletionFilteringEventListener : InlineCompletionEventAdapter {

  private val isApplicable = AtomicBoolean(true)

  protected abstract fun isApplicable(requestEvent: InlineCompletionEventType.Request): Boolean

  final override fun on(event: InlineCompletionEventType) {
    if (event is InlineCompletionEventType.Request) {
      isApplicable.set(isApplicable(event))
    }
    if (isApplicable.get()) {
      super.on(event)
    }
  }
}
