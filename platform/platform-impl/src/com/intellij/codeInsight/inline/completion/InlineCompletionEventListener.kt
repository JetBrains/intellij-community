// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import java.util.*

sealed class InlineCompletionEventType {
  data class Request(val lastInvocation: Long,
                     val request: InlineCompletionRequest,
                     val provider: Class<out InlineCompletionProvider>) : InlineCompletionEventType()

  data class Show(val element: InlineCompletionElement, val i: Int) : InlineCompletionEventType()
  data class Change(val overtypedLength: Int) : InlineCompletionEventType()
  data object Empty : InlineCompletionEventType()
  data class Completion(val cause: Throwable?, val isActive: Boolean) : InlineCompletionEventType()
  data object Insert : InlineCompletionEventType()
  open class Hide(val finishType: FinishType, val isCurrentlyDisplaying: Boolean) : InlineCompletionEventType()
}

interface InlineCompletionEventListener : EventListener {
  fun on(event: InlineCompletionEventType)
}

interface InlineCompletionEventAdapter : InlineCompletionEventListener {
  override fun on(event: InlineCompletionEventType) {
    when (event) {
      is InlineCompletionEventType.Request -> onRequest(event)
      is InlineCompletionEventType.Show -> onShow(event)
      is InlineCompletionEventType.Change -> onChange(event)
      is InlineCompletionEventType.Insert -> onInsert(event)
      is InlineCompletionEventType.Hide -> onHide(event)
      is InlineCompletionEventType.Completion -> onCompletion(event)
      is InlineCompletionEventType.Empty -> onEmpty(event)
    }
  }

  fun onRequest(event: InlineCompletionEventType.Request) {}
  fun onShow(event: InlineCompletionEventType.Show) {}
  fun onChange(event: InlineCompletionEventType.Change) {}
  fun onInsert(event: InlineCompletionEventType.Insert) {}
  fun onHide(event: InlineCompletionEventType.Hide) {}
  fun onCompletion(event: InlineCompletionEventType.Completion) {}
  fun onEmpty(event: InlineCompletionEventType.Empty) {}
}
