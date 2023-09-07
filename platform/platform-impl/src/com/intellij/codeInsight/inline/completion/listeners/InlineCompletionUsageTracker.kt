// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.*
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
class InlineCompletionUsageTracker : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  fun track(scope: CoroutineScope, request: InlineCompletionRequest): InlineCompletionListener = Tracker(scope, request)

  private class Tracker(scope: CoroutineScope, request: InlineCompletionRequest) : InlineCompletionListener {

    private var data: MutableList<EventPair<*>> = mutableListOf()

    private var project: Project? = null
    private var shown: Boolean = false
    private var shownLogSent: Boolean = false

    private var timeTriggered: Long

    init {
      timeTriggered = now
      scope.launch(Dispatchers.EDT) {
        val editor = request.editor
        project = editor.project?.also {
          PsiDocumentManager.getInstance(it).getPsiFile(editor.document)?.let { psiFile ->
            val language = PsiUtilCore.getLanguageAtOffset(psiFile, editor.caretModel.offset)
            add(EventFields.Language.with(language))
            add(EventFields.CurrentFile.with(language))
          }
        }
      }
    }

    private val now: Long
      get() = System.currentTimeMillis()

    private val sinceTriggered: Long
      get() = now - timeTriggered

    override fun on(update: InlineCompletionElementEvent) {
      if (shownLogSent) return

      var shownOutcome: SuggestionOutcome? = null
      when (update) {
        is InlineCompletionElementEvent.Accepted -> { shownOutcome = SuggestionOutcome.ACCEPT }
        is InlineCompletionElementEvent.Rejected -> if (shown) { shownOutcome = SuggestionOutcome.REJECT }
        is InlineCompletionElementEvent.Shown ->  {
          shown = true
          add(SUGGESTION_LENGTH.with(update.element.text.length))
          add(SUGGESTION_TIME_TO_SHOW.with(sinceTriggered))
        }
      }

      shownOutcome?.let {
        add(SUGGESTION_OUTCOME.with(it))
        SHOWN.log(project, data)
        shownLogSent = true
      }
    }

    private fun <T> add(value: EventPair<T>) = data.add(value)

  }

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("inline.completion", 1)
    private const val SHOWN_EVENT_ID = "inline.shown"

    // Suggestions
    private enum class SuggestionOutcome { ACCEPT, REJECT }
    private val SUGGESTION_LENGTH = Int("suggestion_length")
    private val SUGGESTION_TIME_TO_SHOW = Long("suggestion_time_to_show")
    private val SUGGESTION_OUTCOME = Enum<SuggestionOutcome>("suggestion_outcome")

    private val SHOWN: VarargEventId = GROUP.registerVarargEvent(
      SHOWN_EVENT_ID,
      EventFields.Language,
      EventFields.CurrentFile,
      SUGGESTION_LENGTH,
      SUGGESTION_TIME_TO_SHOW,
      SUGGESTION_OUTCOME,
    )
  }
}