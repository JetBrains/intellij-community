// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.util.*

/**
 * Represents information about what's happening with the inline completion state.
 *
 * All the events' constructors are annotated with [ApiStatus.Internal] meaning that you shouldn't create them. The constructors
 * might be changed in the future.
 *
 * This sealed interface might be extended at any point of time. So please, if you have a `when` for [InlineCompletionEventType],
 * use the `else` branch.
 */
sealed class InlineCompletionEventType {

  // General flow

  /**
   * This event is triggered before [InlineCompletionHandler] asks [InlineCompletionProvider] for a suggestion.
   */
  class Request @ApiStatus.Internal constructor(
    val lastInvocation: Long,
    val request: InlineCompletionRequest,
    val provider: Class<out InlineCompletionProvider>,
  ) : InlineCompletionEventType() {
    val requestId: Long
      get() = request.requestId
  }

  /**
   * This event is triggered when a provider either returned no variants, either all the returned variants are empty.
   */
  data object NoVariants : InlineCompletionEventType()

  /**
   * This event is triggered when all the computations of a suggestion are done.
   */
  class Completion @ApiStatus.Internal constructor(val cause: Throwable?, val isActive: Boolean) : InlineCompletionEventType()

  /**
   * This event is triggered before a user inserts a non-empty inline completion variant.
   */
  data object Insert : InlineCompletionEventType()

  /**
   * This event is triggered after a user inserts a non-empty inline completion variant.
   */
  @ApiStatus.Experimental
  data object AfterInsert : InlineCompletionEventType()

  /**
   * This event is triggered when an inline completion session is cleared for any reason (see [finishType]).
   */
  class Hide @ApiStatus.Internal constructor(
    val finishType: FinishType,
    @Deprecated(
      message = """
This value delegates to InlineCompletionContext.isCurrentlyDisplaying(). 
In cases of invalidation (e.g., mismatched typing), the context is already cleared, causing the method to return false, 
which can be misleading. 
Please use other methods of the listener to determine whether completion is or was being shown.
      """,
    )
    @ScheduledForRemoval
    val isCurrentlyDisplaying: Boolean,
  ) : InlineCompletionEventType()

  /**
   * This event is triggered in one of the following cases:
   * * A variant is actually switched using `InlineCompletionSession` (e.g. using shortcuts or UI). Then [explicit] is `true`.
   * * A variant is switched because a currently used variant was computed and turned out to be empty. It happens only
   * for the prefix of the variants until one of them is non-empty. Then [explicit] is `false`.
   * * A currently used variant was invalidated via `InlineCompletionSuggestionUpdateManager`. Then [explicit] is `false`.
   */
  class VariantSwitched @ApiStatus.Internal constructor(
    val fromVariantIndex: Int,
    val toVariantIndex: Int,
    val explicit: Boolean,
  ) : InlineCompletionEventType()

  // Per variant flow

  sealed class PerVariantEventType : InlineCompletionEventType() {
    abstract val variantIndex: Int
  }

  /**
   * This event is triggered when a variant is completely computed.
   */
  class VariantComputed @ApiStatus.Internal constructor(override val variantIndex: Int) : PerVariantEventType()

  /**
   * This event is triggered when an element in a variant is computed. [i] indicates the index of computed element.
   */
  class Computed @ApiStatus.Internal constructor(
    override val variantIndex: Int,
    val element: InlineCompletionElement,
    val i: Int,
  ) : PerVariantEventType()

  /**
   * This event is triggered when an element is shown:
   * * The element is computed while a variant is shown.
   * * A variant is switched and all the elements are shown.
   * * A variant is updated and all the elements are re-rendered and shown again.
   */
  class Show @ApiStatus.Internal constructor(
    override val variantIndex: Int,
    val element: InlineCompletionElement,
    val i: Int,
  ) : PerVariantEventType()

  /**
   * This event is triggered when a variant is updated upon some event.
   * * [lengthChange] indicates the difference between the new length of text and the old length.
   * * [elements] indicates the list of new elements after update.
   */
  class Change @ApiStatus.Internal constructor(
    @ApiStatus.Internal val event: InlineCompletionEvent,
    override val variantIndex: Int,
    @ApiStatus.Internal val elements: List<InlineCompletionElement>,
    val lengthChange: Int,
  ) : PerVariantEventType() {

    @Deprecated(
      message = "Use lengthChange, because now a variant can be updated not only due typings.",
      replaceWith = ReplaceWith("lengthChange")
    )
    val overtypedLength: Int
      @ScheduledForRemoval
      @Deprecated(
        "Use lengthChange, because now a variant can be updated not only due typings.",
        replaceWith = ReplaceWith("lengthChange"),
      )
      get() = lengthChange
  }

  /**
   * This event is triggered when a variant is invalidated during some update.
   */
  class Invalidated @ApiStatus.Internal constructor(@ApiStatus.Internal val event: InlineCompletionEvent, override val variantIndex: Int) : PerVariantEventType()

  /**
   * This event is triggered when a variant is computed and turned out to be completely empty.
   */
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
      is InlineCompletionEventType.AfterInsert -> onAfterInsert(event)
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
  fun onAfterInsert(event: InlineCompletionEventType.AfterInsert) {}
  fun onHide(event: InlineCompletionEventType.Hide) {}
  fun onCompletion(event: InlineCompletionEventType.Completion) {}
  fun onEmpty(event: InlineCompletionEventType.Empty) {}
  fun onVariantComputed(event: InlineCompletionEventType.VariantComputed) {}
  fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {}
}
