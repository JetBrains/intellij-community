// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.lang.Language
import com.intellij.util.application

/**
 * This tracker lives from the moment the inline completion appears on the screen until its end.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionShowTracker(
  private val requestId: Long,
  private val invocationTime: Long,
  private val triggerFeatures: EventPair<*>,
  private val language: Language?,
  private val fileLanguage: Language?,
) {
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
    data.add(EventFields.Language.with(language))
    data.add(EventFields.CurrentFile.with(fileLanguage))
    data.add(ShownEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
    if (application.isEAP) {
      data.add(triggerFeatures)
    }
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
    finish(InlineCompletionFinishType.SELECTED)
  }

  fun canceled(finishType: InlineCompletionFinishType) {
    finish(finishType)
  }

  private fun finish(finishType: InlineCompletionFinishType) {
    if (shownLogSent) {
      return
    }
    shownLogSent = true
    data.add(ShownEvents.LINES.with(lines))
    data.add(ShownEvents.LENGTH.with(suggestionLength))
    data.add(ShownEvents.TYPING_DURING_SHOW.with(typingDuringShow))
    data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
    data.add(ShownEvents.FINISH_TYPE.with(finishType))
    shownEvent.log(data)
  }

  private object ShownEvents {
    val REQUEST_ID = EventFields.Long("request_id")

    val LINES = EventFields.Int("lines")
    val LENGTH = EventFields.Int("length")
    val TYPING_DURING_SHOW = EventFields.Int("typing_during_show")

    val TIME_TO_SHOW = EventFields.Long("time_to_show")
    val SHOWING_TIME = EventFields.Long("showing_time")
    val FINISH_TYPE = EventFields.Enum<InlineCompletionFinishType>("finish_type")
  }

  private val shownEvent: VarargEventId = InlineCompletionUsageTracker.GROUP.registerVarargEvent(
    "shown",
    ShownEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    ShownEvents.LINES,
    ShownEvents.LENGTH,
    ShownEvents.TYPING_DURING_SHOW,
    ShownEvents.TIME_TO_SHOW,
    ShownEvents.SHOWING_TIME,
    ShownEvents.FINISH_TYPE,
    InlineContextFeatures.CONTEXT_FEATURES
  )
}