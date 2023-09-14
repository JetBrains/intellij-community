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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

@ApiStatus.Experimental
object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("inline.completion", 6)

  override fun getGroup() = GROUP

  class Listener : InlineCompletionEventAdapter {
    private val lock = ReentrantLock()
    private var invocationTracker: InvocationTracker? = null
    private var showTracker: ShowTracker? = null

    override fun onRequest(event: InlineCompletionEventType.Request) = lock.withLock {
      invocationTracker = InvocationTracker(event).also {
        application.runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
    }

    override fun onShow(event: InlineCompletionEventType.Show) = lock.withLock {
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

    override fun onChange(event: InlineCompletionEventType.Change) {
      showTracker!!.truncateTyping(event.truncateTyping)
    }

    override fun onInsert(event: InlineCompletionEventType.Insert): Unit = lock.withLock {
      showTracker?.selected()
    }

    override fun onHide(event: InlineCompletionEventType.Hide): Unit = lock.withLock {
      showTracker?.canceled()
    }

    override fun onEmpty(event: InlineCompletionEventType.Empty): Unit = lock.withLock {
      invocationTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion): Unit = lock.withLock {
      if (!event.isActive || event.cause is CancellationException || event.cause is ProcessCanceledException) {
        invocationTracker?.canceled()
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
   * This tracker is not thread-safe.
   */
  private class InvocationTracker(
    private val invocationTime: Long,
    private val request: InlineCompletionRequest,
    private val provider: Class<out InlineCompletionProvider>
  ) {
    constructor(event: InlineCompletionEventType.Request) : this(event.lastInvocation, event.request, event.provider)

    val requestId = Random.nextLong()
    private var finished = false
    private val data = mutableListOf<EventPair<*>>()
    private val contextFeatures = mutableListOf<EventPair<*>>()
    private var hasSuggestions: Boolean? = null
    private var canceled: Boolean = false
    private var exception: Boolean = false

    fun createShowTracker() = ShowTracker(requestId, invocationTime, InlineContextFeatures.getEventPair(contextFeatures))

    fun captureContext(editor: Editor, offset: Int) {
      val psiFile = PsiDocumentManager.getInstance(editor.project ?: return).getPsiFile(editor.document) ?: return
      val language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
      data.add(EventFields.Language.with(language))
      data.add(EventFields.CurrentFile.with(psiFile.language))
      InlineContextFeatures.capture(editor, offset, contextFeatures)
      assert(!finished)
    }

    fun noSuggestions() {
      hasSuggestions = false
      assert(!finished)
    }

    fun hasSuggestion() {
      hasSuggestions = true
      assert(!finished)
    }

    fun canceled() {
      canceled = true
      assert(!finished)
    }

    fun exception() {
      exception = true
      assert(!finished)
    }

    fun finished() {
      if (finished) {
        error("Already finished")
      }
      finished = true
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
            canceled -> InvokedEvents.Outcome.CANCELED
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
      CANCELED,
      SHOW,
      NO_SUGGESTIONS
    }
  }

  private val InvokedEvent: VarargEventId = GROUP.registerVarargEvent(
    "invoked",
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
   * This tracker is not thread-safe.
   */
  private class ShowTracker(private val requestId: Long,
                            private val invocationTime: Long,
                            private val triggerFeatures: EventPair<*>) {
    private val data = mutableListOf<EventPair<*>>()
    private var firstShown = false
    private var shownLogSent = false
    private var showStartTime = 0L
    private var suggestionLength = 0
    private var lines = 0
    private var typingDuringShow = 0

    fun firstShown(element: InlineCompletionElement) {
      if (firstShown) {
        error("Already first shown")
      }
      firstShown = true
      showStartTime = System.currentTimeMillis()
      data.add(ShownEvents.REQUEST_ID.with(requestId))
      data.add(ShownEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
      data.add(triggerFeatures)
      nextShown(element)
      assert(!shownLogSent)
    }

    fun nextShown(element: InlineCompletionElement) {
      assert(firstShown) {
        "Call firstShown firstly"
      }
      lines += (element.text.lines().size - 1).coerceAtLeast(0)
      if (suggestionLength == 0 && element.text.isNotEmpty()) {
        lines++ // first line
      }
      suggestionLength += element.text.length
      assert(!shownLogSent)
    }

    fun truncateTyping(truncateTyping: Int) {
      assert(firstShown)
      typingDuringShow += truncateTyping
      assert(!shownLogSent)
    }

    fun selected() {
      finish(ShownEvents.FinishType.SELECTED)
    }

    fun canceled() {
      finish(ShownEvents.FinishType.CANCELED)
    }

    private fun finish(finishType: ShownEvents.FinishType) {
      if (shownLogSent) {
        return
      }
      shownLogSent = true
      data.add(ShownEvents.LINES.with(lines))
      data.add(ShownEvents.LENGTH.with(suggestionLength))
      data.add(ShownEvents.TYPING_DURING_SHOW.with(typingDuringShow))
      data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
      data.add(ShownEvents.FINISH_TYPE.with(finishType))
      ShownEvent.log(data)
    }
  }

  private object ShownEvents {
    val REQUEST_ID = Long("request_id")

    val LINES = Int("lines")
    val LENGTH = Int("length")
    val TYPING_DURING_SHOW = Int("typing_during_show")

    val TIME_TO_SHOW = Long("time_to_show")
    val SHOWING_TIME = Long("showing_time")
    val FINISH_TYPE = Enum<FinishType>("finish_type")

    enum class FinishType { SELECTED, CANCELED }
  }

  private val ShownEvent: VarargEventId = GROUP.registerVarargEvent(
    "shown",
    ShownEvents.REQUEST_ID,
    ShownEvents.LINES,
    ShownEvents.LENGTH,
    ShownEvents.TYPING_DURING_SHOW,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.FINISH_TYPE,
    InlineContextFeatures.CONTEXT_FEATURES
  )
}
