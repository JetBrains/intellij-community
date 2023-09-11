// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionElement
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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

@ApiStatus.Experimental
object InlineCompletionUsageTracker : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("inline.completion", 3)

  override fun getGroup() = GROUP

  class Listener : InlineCompletionEventAdapter {
    private var triggerTracker: TriggerTracker? = null
    private var showTracker: ShowTracker? = null
    private var editor: Editor? = null

    override fun onRequest(event: InlineCompletionEventType.Request) {
      editor = event.request.editor
      triggerTracker = TriggerTracker(event).also {
        runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
    }

    override fun onShow(event: InlineCompletionEventType.Show) {
      if (event.i == 0 && !event.element.text.isEmpty()) {
        triggerTracker?.hasSuggestion()
      }

      if (triggerTracker != null) {
        showTracker = ShowTracker(triggerTracker!!.invocationTime, triggerTracker!!.requestId, )
      }

      editor?.let { showTracker?.shown(it, event.element) }
    }

    override fun onInsert(event: InlineCompletionEventType.Insert) {
      showTracker?.accepted()
    }

    override fun onHide(event: InlineCompletionEventType.Hide) {
      showTracker?.rejected()
    }

    override fun onEmpty(event: InlineCompletionEventType.Empty) {
      triggerTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion) {
      if (event.cause is CancellationException || event.cause is ProcessCanceledException) {
        triggerTracker?.cancelled()
        return
      }
      else if (event.cause != null) {
        triggerTracker?.exception()
        return
      }
      editor?.let {
        triggerTracker?.finished(it.project)
      }
    }
  }

  /**
   * This tracker lives from the moment the inline completion triggered until it appears on the screen or showing will be cancelled.
   */
  class TriggerTracker(
    val invocationTime: Long,
    private val request: InlineCompletionRequest,
    private val provider: Class<out InlineCompletionProvider>
  ) {
    constructor(event: InlineCompletionEventType.Request) : this(event.lastInvocation, event.request, event.provider)

    val requestId = Random.nextLong()
    private val finished = AtomicBoolean(false)
    private val data = mutableListOf<EventPair<*>>()
    private var hasSuggestions: Boolean? = null
    private var cancelled: Boolean = false
    private var exception: Boolean = false

    @RequiresEdt
    fun captureContext(editor: Editor, offset: Int) {
      val psiFile = PsiDocumentManager.getInstance(editor.project ?: return).getPsiFile(editor.document) ?: return
      val language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
      data.add(EventFields.Language.with(language))
      data.add(EventFields.CurrentFile.with(psiFile.language))
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

    fun finished(project: Project?) {
      if (!finished.compareAndSet(false, true)) {
        error("Already finished")
      }
      TriggeredEvent.log(project, listOf(
        TriggeredEvents.REQUEST_ID.with(requestId),
        *data.toTypedArray(),
        TriggeredEvents.EVENT.with(request.event::class.java),
        TriggeredEvents.PROVIDER.with(provider::class.java),
        TriggeredEvents.TIME_TO_COMPUTE.with(System.currentTimeMillis() - invocationTime),
        TriggeredEvents.OUTCOME.with(
          when {
            // fixed order
            exception -> TriggeredEvents.Outcome.EXCEPTION
            cancelled -> TriggeredEvents.Outcome.CANCELLED
            hasSuggestions == true -> TriggeredEvents.Outcome.SHOW
            hasSuggestions == false -> TriggeredEvents.Outcome.NO_SUGGESTIONS
            else -> null
          }
        )
      ))
    }
  }

  private object TriggeredEvents {
    const val TRIGGERED_EVENT_ID = "inline.triggered"

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

  private val TriggeredEvent: VarargEventId = GROUP.registerVarargEvent(
    TriggeredEvents.TRIGGERED_EVENT_ID,
    TriggeredEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    TriggeredEvents.EVENT,
    TriggeredEvents.PROVIDER,
    TriggeredEvents.TIME_TO_COMPUTE,
    TriggeredEvents.OUTCOME,
  )

  /**
   * This tracker lives from the moment the inline completion appears on the screen until its end.
   */
  class ShowTracker(private val invocationTime: Long, private val requestId: Long) {
    private val data = mutableListOf<EventPair<*>>()
    private val shown = AtomicBoolean(false)
    private val shownLogSent = AtomicBoolean(false)
    private var project: Project? = null
    private var showStartTime = 0L

    fun shown(editor: Editor, element: InlineCompletionElement) {
      if (!shown.compareAndSet(false, true)) {
        error("Already shown")
      }
      showStartTime = System.currentTimeMillis()
      data.add(ShownEvents.REQUEST_ID.with(requestId))
      project = editor.project
      data.add(ShownEvents.SUGGESTION_LENGTH.with(element.text.length))
      data.add(ShownEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
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
      data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
      data.add(ShownEvents.OUTCOME.with(outcome))
      ShownEvent.log(project, data)
    }
  }

  private object ShownEvents {
    const val SHOWN_EVENT_ID = "inline.shown"

    val REQUEST_ID = Long("request_id")

    val SUGGESTION_LENGTH = Int("suggestion_length")

    val TIME_TO_SHOW = Long("time_to_show")
    val SHOWING_TIME = Long("showing_time")
    val OUTCOME = Enum<Outcome>("outcome")

    enum class Outcome { ACCEPT, REJECT }
  }

  private val ShownEvent: VarargEventId = GROUP.registerVarargEvent(
    ShownEvents.SHOWN_EVENT_ID,
    ShownEvents.REQUEST_ID,
    ShownEvents.SUGGESTION_LENGTH,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.OUTCOME,
  )
}
