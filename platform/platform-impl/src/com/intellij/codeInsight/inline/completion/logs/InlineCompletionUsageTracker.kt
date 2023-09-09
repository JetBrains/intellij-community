// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random


@ApiStatus.Experimental
class InlineCompletionUsageTracker : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  /**
   * This tracker lives from the moment the inline completion triggered until it appears on the screen or showing will be cancelled.
   */
  class TriggerTracker(
    val invocationTime: Long,
    private val event: InlineCompletionEvent,
    private val provider: InlineCompletionProvider
  ) {
    private val finished = AtomicBoolean(false)
    private var hasSuggestions: Boolean? = null
    private var cancelled: Boolean = false
    private var exception: Boolean = false
    val requestId = Random.nextLong()

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
      TRIGGERED.log(project, listOf(
        REQUEST_ID.with(requestId),
        EVENT.with(event::class.java),
        PROVIDER.with(provider::class.java),
        OUTCOME.with(
          when {
            // fixed order
            exception -> Outcome.EXCEPTION
            cancelled -> Outcome.CANCELLED
            hasSuggestions == true -> Outcome.SHOW
            hasSuggestions == false -> Outcome.NO_SUGGESTIONS
            else -> null
          }
        ),
        TIME_TO_COMPUTE.with(System.currentTimeMillis() - invocationTime)
      ))
    }

    private companion object {
      const val TRIGGERED_EVENT_ID = "inline.triggered"

      enum class Outcome {
        EXCEPTION,
        CANCELLED,
        SHOW,
        NO_SUGGESTIONS
      }

      val REQUEST_ID = Long("request_id")
      val EVENT = EventFields.Class("event")
      val PROVIDER = EventFields.Class("event")
      val OUTCOME = NullableEnum<Outcome>("outcome")
      val TIME_TO_COMPUTE = Long("time_to_compute")

      val TRIGGERED: VarargEventId = GROUP.registerVarargEvent(
        TRIGGERED_EVENT_ID,
        REQUEST_ID,
        EventFields.Language,
        EventFields.CurrentFile,
        EVENT,
        PROVIDER,
        OUTCOME,
        TIME_TO_COMPUTE,
      )
    }
  }

  /**
   * This tracker lives from the moment the inline completion appears on the screen until its end.
   */
  class ShowTracker(private val invocationTime: Long, private val requestId: Long) {
    private var data: MutableList<EventPair<*>> = mutableListOf()
    private val shown = AtomicBoolean(false)
    private val shownLogSent = AtomicBoolean(false)
    private var project: Project? = null
    private var showStartTime = 0L

    @RequiresEdt
    fun shown(editor: Editor, element: InlineCompletionElement) {
      if (!shown.compareAndSet(false, true)) {
        error("Already shown")
      }
      showStartTime = System.currentTimeMillis()
      data.add(REQUEST_ID.with(requestId))
      project = editor.project?.also {
        PsiDocumentManager.getInstance(it).getPsiFile(editor.document)?.let { psiFile ->
          val language = PsiUtilCore.getLanguageAtOffset(psiFile, editor.caretModel.offset)
          data.add(EventFields.Language.with(language))
          data.add(EventFields.CurrentFile.with(language))
        }
      }
      data.add(SUGGESTION_LENGTH.with(element.text.length))
      data.add(TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
      assert(!shownLogSent.get())
    }

    fun accepted() {
      finish(Decision.ACCEPT)
    }

    fun rejected() {
      finish(Decision.REJECT)
    }

    private fun finish(decision: Decision) {
      if (!shownLogSent.compareAndSet(false, true)) {
        error("Already sent")
      }
      data.add(DECISION.with(decision))
      data.add(SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
      SHOWN.log(project, data)
    }

    private companion object {
      const val SHOWN_EVENT_ID = "inline.shown"

      enum class Decision { ACCEPT, REJECT }

      val REQUEST_ID = Long("request_id")

      val SUGGESTION_LENGTH = Int("suggestion_length")
      val TIME_TO_SHOW = Long("time_to_show")
      val SHOWING_TIME = Long("showing_time")
      val DECISION = Enum<Decision>("decision")

      val SHOWN: VarargEventId = GROUP.registerVarargEvent(
        SHOWN_EVENT_ID,
        REQUEST_ID,
        EventFields.Language,
        EventFields.CurrentFile,
        SUGGESTION_LENGTH,
        TIME_TO_SHOW,
        SHOWING_TIME,
        DECISION,
      )
    }
  }

  private companion object {
    private val GROUP: EventLogGroup = EventLogGroup("inline.completion", 3)
  }
}