// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.time.Duration

/**
 * This tracker lives from the moment the inline completion appears on the screen until its end.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionShowTracker(
  private val request: InlineCompletionRequest,
  private val requestId: Long,
  private val provider: Class<out InlineCompletionProvider>,
  private val invocationTime: Long,
  private val language: Language?,
  private val fileLanguage: Language?,
) {
  private val data = mutableListOf<EventPair<*>>()
  private var showLogSent = false
  private var showStartTime = 0L
  private val variantStates = mutableListOf<VariantState>()
  private var lastOffset = request.endOffset
  private var switchingVariantsTimes = 0
  private var potentiallySelectedIndex: Int? = null

  fun firstComputed(variantIndex: Int, element: InlineCompletionElement) {
    extendVariantsNumber(variantIndex + 1)

    if (potentiallySelectedIndex == null) {
      potentiallySelectedIndex = variantIndex // It's the first variant that will be shown to a user
    }

    val state = variantStates[variantIndex]
    if (state.firstComputed) {
      error("First element for $variantIndex index was already computed.")
    }
    state.firstComputed = true
    showStartTime = System.currentTimeMillis()
    data.add(ShownEvents.REQUEST_ID.with(requestId))
    data.add(EventFields.Language.with(language))
    data.add(EventFields.CurrentFile.with(fileLanguage))
    data.add(ShownEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
    data.add(ShownEvents.PROVIDER.with(provider))
    nextComputed(variantIndex, element)
    assert(!showLogSent)
  }

  fun nextComputed(variantIndex: Int, element: InlineCompletionElement) {
    val state = variantStates[variantIndex]
    assert(state.firstComputed) {
      "Call firstComputed firstly"
    }
    state.lines += (element.text.lines().size - 1).coerceAtLeast(0)
    if (state.initialSuggestion.isEmpty() && element.text.isNotEmpty()) {
      state.lines++ // first line
    }
    state.append(element.text)
    assert(!showLogSent)
  }

  // Usually, only typings (if providers don't override behaviour)
  fun suggestionChanged(variantIndex: Int, change: Int, newText: String) {
    val state = variantStates[variantIndex]
    state.lengthChange += change
    state.finalSuggestion = newText
    lastOffset = InlineCompletionContext.getOrNull(request.editor)!!.expectedStartOffset // TODO should not depend on context
    assert(!showLogSent)
  }

  fun variantSwitched(toIndex: Int, explicit: Boolean) {
    potentiallySelectedIndex = toIndex
    if (explicit) {
      switchingVariantsTimes++
    }
  }

  fun selected() {
    finish(FinishType.SELECTED)
  }

  fun inserted() {
    potentiallySelectedIndex?.let {
      service<InsertedStateTracker>().track(
        requestId,
        language,
        fileLanguage,
        request.editor,
        initialOffset = request.endOffset,
        insertOffset = lastOffset,
        variantStates[it].finalSuggestion,
        getDurations(),
      )
    }
  }

  fun canceled(finishType: FinishType) {
    finish(finishType)
  }

  private fun finish(finishType: FinishType) {
    if (showLogSent) {
      return
    }
    showLogSent = true
    data.add(ShownEvents.LINES.with(variantStates.map { it.lines }))
    data.add(ShownEvents.LENGTH.with(variantStates.map { it.initialSuggestion.length }))
    data.add(ShownEvents.LENGTH_CHANGE_DURING_SHOW.with(variantStates.maxOf { it.lengthChange }))
    data.add(ShownEvents.SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
    data.add(ShownEvents.FINISH_TYPE.with(finishType))
    data.add(ShownEvents.EXPLICIT_SWITCHING_VARIANTS_TIMES.with(switchingVariantsTimes))

    if (finishType == FinishType.SELECTED || finishType == FinishType.TYPED) {
      potentiallySelectedIndex?.let {
        data.add(ShownEvents.SELECTED_INDEX.with(it))
      }
    }

    InlineCompletionUsageTracker.SHOWN_EVENT.log(request.editor.project, data)
  }

  private fun getDurations(): List<Duration> =
    if (ApplicationManager.getApplication().isUnitTestMode) {
      listOf(Duration.ofMillis(TEST_CHECK_STATE_AFTER_MLS))
    } else {
      listOf(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5))
    }

  private fun extendVariantsNumber(atLeast: Int) {
    while (variantStates.size < atLeast) {
      variantStates += VariantState("", "", 0, 0, false)
    }
  }

  private data class VariantState(
    var initialSuggestion: String,
    var finalSuggestion: String,
    var lines: Int,
    var lengthChange: Int,
    var firstComputed: Boolean
  ) {
    fun append(text: String) {
      initialSuggestion += text
      finalSuggestion += text
    }
  }
}


@ApiStatus.Internal
const val TEST_CHECK_STATE_AFTER_MLS: Long = 100
