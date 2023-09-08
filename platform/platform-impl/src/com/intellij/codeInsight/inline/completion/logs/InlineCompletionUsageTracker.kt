// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
class InlineCompletionUsageTracker : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  /**
   * This tracker lives from the moment the inline completion appears on the screen until its end.
   */
  internal class ShowTracker {
    private var data: MutableList<EventPair<*>> = mutableListOf()
    private var project: Project? = null
    private var shown: Boolean = false
    private var shownLogSent: Boolean = false

    @RequiresEdt
    fun shown(invocationTime: Long, editor: Editor, element: InlineCompletionElement) {
      assert(!shownLogSent)
      shown = true
      project = editor.project?.also {
        PsiDocumentManager.getInstance(it).getPsiFile(editor.document)?.let { psiFile ->
          val language = PsiUtilCore.getLanguageAtOffset(psiFile, editor.caretModel.offset)
          data.add(EventFields.Language.with(language))
          data.add(EventFields.CurrentFile.with(language))
        }
      }
      data.add(SUGGESTION_LENGTH.with(element.text.length))
      data.add(TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
    }

    fun accepted() {
      finish(Decision.ACCEPT)
    }

    fun rejected() {
      finish(Decision.REJECT)
    }

    private fun finish(decision: Decision) {
      data.add(DECISION.with(decision))
      SHOWN.log(project, data)
      shownLogSent = true
    }
  }

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("inline.completion", 2)
    private const val SHOWN_EVENT_ID = "inline.shown"

    // Suggestions
    private enum class Decision { ACCEPT, REJECT }

    private val SUGGESTION_LENGTH = Int("suggestion_length")
    private val TIME_TO_SHOW = Long("time_to_show")
    private val DECISION = Enum<Decision>("decision")

    private val SHOWN: VarargEventId = GROUP.registerVarargEvent(
      SHOWN_EVENT_ID,
      EventFields.Language,
      EventFields.CurrentFile,
      SUGGESTION_LENGTH,
      TIME_TO_SHOW,
      DECISION,
    )
  }
}