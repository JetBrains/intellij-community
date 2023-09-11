// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
sealed class InlineCompletionEventType {
  class Request(val lastInvocation: Long, val request: InlineCompletionRequest, val provider: Class<out InlineCompletionProvider>) : InlineCompletionEventType()
  class Show(val element: InlineCompletionElement, val i: Int) : InlineCompletionEventType()
  object Empty : InlineCompletionEventType()
  class Completion(val cause: Throwable?) : InlineCompletionEventType()
  object Insert : InlineCompletionEventType()
  open class Hide(val explicit: Boolean) : InlineCompletionEventType()
}

@ApiStatus.Experimental
interface InlineCompletionEventListener : EventListener {
  fun on(event: InlineCompletionEventType)
}

@ApiStatus.Experimental
interface InlineCompletionEventAdapter : InlineCompletionEventListener {
  override fun on(event: InlineCompletionEventType) {
    when (event) {
      is InlineCompletionEventType.Request -> onRequest(event)
      is InlineCompletionEventType.Show -> onShow(event)
      is InlineCompletionEventType.Insert -> onInsert(event)
      is InlineCompletionEventType.Hide -> onHide(event)
      is InlineCompletionEventType.Completion -> onCompletion(event)
      is InlineCompletionEventType.Empty -> onEmpty(event)
    }
  }

  fun onRequest(event: InlineCompletionEventType.Request) {}
  fun onShow(event: InlineCompletionEventType.Show) {}
  fun onInsert(event: InlineCompletionEventType.Insert) {}
  fun onHide(event: InlineCompletionEventType.Hide) {}
  fun onCompletion(event: InlineCompletionEventType.Completion) {}
  fun onEmpty(event: InlineCompletionEventType.Empty) {}
}
