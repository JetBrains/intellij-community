// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import org.jetbrains.annotations.ApiStatus
import java.util.*

// TODO support new events
// TODO better names
sealed class InlineCompletionEventType {

  sealed class PerVariantEventType : InlineCompletionEventType()

  class Request @ApiStatus.Internal constructor(
    val lastInvocation: Long,
    val request: InlineCompletionRequest,
    val provider: Class<out InlineCompletionProvider>
  ) : InlineCompletionEventType()

  data object NoVariants : InlineCompletionEventType()

  class Computed @ApiStatus.Internal constructor(val element: InlineCompletionElement, val i: Int) : PerVariantEventType()

  class Show @ApiStatus.Internal constructor(val element: InlineCompletionElement, val i: Int) : PerVariantEventType()

  class Change @ApiStatus.Internal constructor(val overtypedLength: Int) : PerVariantEventType()

  data object Empty : PerVariantEventType()

  class Completion @ApiStatus.Internal constructor(val cause: Throwable?, val isActive: Boolean) : PerVariantEventType()

  class VariantComputed(val i: Int) : PerVariantEventType()

  data object Insert : InlineCompletionEventType()

  class Hide @ApiStatus.Internal constructor(val finishType: FinishType, val isCurrentlyDisplaying: Boolean) : InlineCompletionEventType()
}

interface InlineCompletionEventListener : EventListener {
  fun on(event: InlineCompletionEventType)
}

interface InlineCompletionEventAdapter : InlineCompletionEventListener {
  override fun on(event: InlineCompletionEventType) {
    when (event) {
      is InlineCompletionEventType.Request -> onRequest(event)
      is InlineCompletionEventType.NoVariants -> onNoVariants(event)
      is InlineCompletionEventType.Computed -> onComputed(event)
      is InlineCompletionEventType.Show -> onShow(event)
      is InlineCompletionEventType.Change -> onChange(event)
      is InlineCompletionEventType.Insert -> onInsert(event)
      is InlineCompletionEventType.Hide -> onHide(event)
      is InlineCompletionEventType.Completion -> onCompletion(event)
      is InlineCompletionEventType.Empty -> onEmpty(event)
      is InlineCompletionEventType.VariantComputed -> onVariantComputed(event)
    }
  }

  fun onRequest(event: InlineCompletionEventType.Request) {}
  fun onNoVariants(event: InlineCompletionEventType.NoVariants) {}
  fun onComputed(event: InlineCompletionEventType.Computed) {}
  fun onShow(event: InlineCompletionEventType.Show) {}
  fun onChange(event: InlineCompletionEventType.Change) {}
  fun onInsert(event: InlineCompletionEventType.Insert) {}
  fun onHide(event: InlineCompletionEventType.Hide) {}
  fun onCompletion(event: InlineCompletionEventType.Completion) {}
  fun onEmpty(event: InlineCompletionEventType.Empty) {}
  fun onVariantComputed(event: InlineCompletionEventType.VariantComputed) {}
}
