// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import org.jetbrains.annotations.ApiStatus
import java.util.*

// TODO better names
sealed class InlineCompletionEventType {

  // General flow

  class Request @ApiStatus.Internal constructor(
    val lastInvocation: Long,
    val request: InlineCompletionRequest,
    val provider: Class<out InlineCompletionProvider>
  ) : InlineCompletionEventType()

  // TODO explain semantics: either no variants, either all are empty. Maybe change name
  data object NoVariants : InlineCompletionEventType()

  class Completion @ApiStatus.Internal constructor(val cause: Throwable?, val isActive: Boolean) : InlineCompletionEventType()

  data object Insert : InlineCompletionEventType()

  class Hide @ApiStatus.Internal constructor(val finishType: FinishType, val isCurrentlyDisplaying: Boolean) : InlineCompletionEventType()

  // TODO docs when it triggers
  class VariantSwitched @ApiStatus.Internal constructor(
    val fromVariantIndex: Int,
    val toVariantIndex: Int,
    val explicit: Boolean
  ) : InlineCompletionEventType()

  // Per variant flow

  sealed class PerVariantEventType : InlineCompletionEventType() {
    abstract val variantIndex: Int
  }

  class VariantComputed @ApiStatus.Internal constructor(override val variantIndex: Int) : PerVariantEventType()

  class Computed @ApiStatus.Internal constructor(
    override val variantIndex: Int,
    val element: InlineCompletionElement,
    val i: Int
  ) : PerVariantEventType()

  class Show @ApiStatus.Internal constructor(
    override val variantIndex: Int,
    val element: InlineCompletionElement,
    val i: Int
  ) : PerVariantEventType()

  class Change @ApiStatus.Internal constructor(
    override val variantIndex: Int,
    val lengthChange: Int
  ) : PerVariantEventType() {

    @Deprecated(message = "") // TODO
    val overtypedLength: Int
      get() = lengthChange
  }

  // TODO docs
  class Invalidated @ApiStatus.Internal constructor(override val variantIndex: Int) : PerVariantEventType()

  class Empty @ApiStatus.Internal constructor(override val variantIndex: Int) : PerVariantEventType()
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
      is InlineCompletionEventType.Invalidated -> onInvalidated(event)
      is InlineCompletionEventType.Insert -> onInsert(event)
      is InlineCompletionEventType.Hide -> onHide(event)
      is InlineCompletionEventType.Completion -> onCompletion(event)
      is InlineCompletionEventType.Empty -> onEmpty(event)
      is InlineCompletionEventType.VariantComputed -> onVariantComputed(event)
      is InlineCompletionEventType.VariantSwitched -> onVariantSwitched(event)
    }
  }

  fun onRequest(event: InlineCompletionEventType.Request) {}
  fun onNoVariants(event: InlineCompletionEventType.NoVariants) {}
  fun onComputed(event: InlineCompletionEventType.Computed) {}
  fun onShow(event: InlineCompletionEventType.Show) {}
  fun onChange(event: InlineCompletionEventType.Change) {}
  fun onInvalidated(event: InlineCompletionEventType.Invalidated) {}
  fun onInsert(event: InlineCompletionEventType.Insert) {}
  fun onHide(event: InlineCompletionEventType.Hide) {}
  fun onCompletion(event: InlineCompletionEventType.Completion) {}
  fun onEmpty(event: InlineCompletionEventType.Empty) {}
  fun onVariantComputed(event: InlineCompletionEventType.VariantComputed) {}
  fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {}
}
