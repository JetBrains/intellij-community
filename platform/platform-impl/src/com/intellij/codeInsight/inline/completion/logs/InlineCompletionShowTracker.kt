// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language

/**
 * This tracker lives from the moment the inline completion appears on the screen until its end.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionShowTracker(
  private val requestId: Long,
  private val provider: Class<out InlineCompletionProvider>,
  private val invocationTime: Long,
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
    data.add(ShownEvents.PROVIDER.with(provider))
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
    finish(FinishType.SELECTED)
  }

  fun canceled(finishType: FinishType) {
    finish(finishType)
  }

  private fun finish(finishType: FinishType) {
    if (shownLogSent) {
      return
    }
    shownLogSent = true
    data.add(ShownEvents.LINES.with(lines))
    data.add(ShownEvents.LENGTH.with(suggestionLength))
    data.add(ShownEvents.TYPING_DURING_SHOW.with(typingDuringShow))
    data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
    data.add(ShownEvents.FINISH_TYPE.with(finishType))
    InlineCompletionUsageTracker.SHOWN_EVENT.log(data)
  }
}