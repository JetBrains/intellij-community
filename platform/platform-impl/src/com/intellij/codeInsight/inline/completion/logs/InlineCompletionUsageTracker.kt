// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.eventLog.events.EventFields.NullableEnum
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

@ApiStatus.Experimental
object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("inline.completion", 4)

  override fun getGroup() = GROUP

  class Listener : InlineCompletionEventAdapter {
    private var invocationTracker: InvocationTracker? = null
    private var showTracker: ShowTracker? = null

    override fun onRequest(event: InlineCompletionEventType.Request) {
      invocationTracker = InvocationTracker(event).also {
        runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
    }

    override fun onShow(event: InlineCompletionEventType.Show) {
      if (event.i == 0 && !event.element.text.isEmpty()) {
        invocationTracker?.hasSuggestion()
      }
      if (event.i == 0) {
        // invocation tracker -> show tracker
        showTracker = invocationTracker?.createShowTracker()
        showTracker!!.firstShown(event.element)
      }
      if (event.i != 0) {
        showTracker!!.nextShown(event.element)
      }
    }

    override fun onInsert(event: InlineCompletionEventType.Insert) {
      showTracker?.accepted()
    }

    override fun onHide(event: InlineCompletionEventType.Hide) {
      showTracker?.rejected()
    }

    override fun onEmpty(event: InlineCompletionEventType.Empty) {
      invocationTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion) {
      if (!event.isActive || event.cause is CancellationException || event.cause is ProcessCanceledException) {
        invocationTracker?.cancelled()
      }
      else if (event.cause != null) {
        invocationTracker?.exception()
      }
      invocationTracker?.finished()
      invocationTracker = null
    }
  }

  /**
   * This tracker lives from the moment the inline completion is invoked until the end of generation.
   */
  private class InvocationTracker(
    private val invocationTime: Long,
    private val request: InlineCompletionRequest,
    private val provider: Class<out InlineCompletionProvider>
  ) {
    constructor(event: InlineCompletionEventType.Request) : this(event.lastInvocation, event.request, event.provider)

    val requestId = Random.nextLong()
    private val finished = AtomicBoolean(false)
    private val data = mutableListOf<EventPair<*>>()
    private val triggerFeatures = mutableListOf<EventPair<*>>()
    private var hasSuggestions: Boolean? = null
    private var cancelled: Boolean = false
    private var exception: Boolean = false

    fun createShowTracker() = ShowTracker(requestId, invocationTime, InlineTriggerFeatures.getEventPair(triggerFeatures))

    fun captureContext(editor: Editor, offset: Int) {
      val psiFile = PsiDocumentManager.getInstance(editor.project ?: return).getPsiFile(editor.document) ?: return
      val language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
      data.add(EventFields.Language.with(language))
      data.add(EventFields.CurrentFile.with(psiFile.language))
      InlineTriggerFeatures.capture(editor, offset, triggerFeatures)
      assert(!finished.get())
    }

    fun noSuggestions() {
      hasSuggestions = false
      assert(!finished.get())
    }

    fun hasSuggestion() {
      hasSuggestions = true
      assert(!finished.get())
    }

    fun cancelled() {
      cancelled = true
      assert(!finished.get())
    }

    fun exception() {
      exception = true
      assert(!finished.get())
    }

    fun finished() {
      if (!finished.compareAndSet(false, true)) {
        error("Already finished")
      }
      InvokedEvent.log(listOf(
        InvokedEvents.REQUEST_ID.with(requestId),
        *data.toTypedArray(),
        InvokedEvents.EVENT.with(request.event::class.java),
        InvokedEvents.PROVIDER.with(provider),
        InvokedEvents.TIME_TO_COMPUTE.with(System.currentTimeMillis() - invocationTime),
        InvokedEvents.OUTCOME.with(
          when {
            // fixed order
            exception -> InvokedEvents.Outcome.EXCEPTION
            cancelled -> InvokedEvents.Outcome.CANCELLED
            hasSuggestions == true -> InvokedEvents.Outcome.SHOW
            hasSuggestions == false -> InvokedEvents.Outcome.NO_SUGGESTIONS
            else -> null
          }
        )
      ))
    }
  }

  private object InvokedEvents {
    val REQUEST_ID = Long("request_id")
    val EVENT = EventFields.Class("event")
    val PROVIDER = EventFields.Class("provider")
    val TIME_TO_COMPUTE = Long("time_to_compute")
    val OUTCOME = NullableEnum<Outcome>("outcome")

    enum class Outcome {
      EXCEPTION,
      CANCELLED,
      SHOW,
      NO_SUGGESTIONS
    }
  }

  private val InvokedEvent: VarargEventId = GROUP.registerVarargEvent(
    "inline.invoked",
    InvokedEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    InvokedEvents.EVENT,
    InvokedEvents.PROVIDER,
    InvokedEvents.TIME_TO_COMPUTE,
    InvokedEvents.OUTCOME,
  )

  /**
   * This tracker lives from the moment the inline completion appears on the screen until its end.
   */
  private class ShowTracker(private val requestId: Long,
                            private val invocationTime: Long,
                            private val triggerFeatures: EventPair<*>) {
    private val data = mutableListOf<EventPair<*>>()
    private val firstShown = AtomicBoolean(false)
    private val shownLogSent = AtomicBoolean(false)
    private var showStartTime = 0L
    private var suggestionLength = 0

    fun firstShown(element: InlineCompletionElement) {
      if (!firstShown.compareAndSet(false, true)) {
        error("Already first shown")
      }
      showStartTime = System.currentTimeMillis()
      data.add(ShownEvents.REQUEST_ID.with(requestId))
      data.add(ShownEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
      data.add(triggerFeatures)
      nextShown(element)
      assert(!shownLogSent.get())
    }

    fun nextShown(element: InlineCompletionElement) {
      assert(firstShown.get()) {
        "Call firstShown firstly"
      }
      suggestionLength += element.text.length
      assert(!shownLogSent.get())
    }

    fun accepted() {
      finish(ShownEvents.Outcome.ACCEPT)
    }

    fun rejected() {
      finish(ShownEvents.Outcome.REJECT)
    }

    private fun finish(outcome: ShownEvents.Outcome) {
      if (!shownLogSent.compareAndSet(false, true)) {
        return
      }
      data.add(ShownEvents.SUGGESTION_LENGTH.with(suggestionLength))
      data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
      data.add(ShownEvents.OUTCOME.with(outcome))
      ShownEvent.log(data)
    }
  }

  private object ShownEvents {
    val REQUEST_ID = Long("request_id")

    val SUGGESTION_LENGTH = Int("suggestion_length")

    val TIME_TO_SHOW = Long("time_to_show")
    val SHOWING_TIME = Long("showing_time")
    val OUTCOME = Enum<Outcome>("outcome")

    enum class Outcome { ACCEPT, REJECT }
  }

  private val ShownEvent: VarargEventId = GROUP.registerVarargEvent(
    "inline.shown",
    ShownEvents.REQUEST_ID,
    ShownEvents.SUGGESTION_LENGTH,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.OUTCOME,
    InlineTriggerFeatures.TRIGGER_FEATURES
  )
}
