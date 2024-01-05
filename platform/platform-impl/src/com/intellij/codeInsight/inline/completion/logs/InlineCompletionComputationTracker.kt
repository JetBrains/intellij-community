// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ComputedEvents
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ComputedEvents.FinishType
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language

/**
 * This tracker lives from the moment the inline completion appears on the screen until its end.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionComputationTracker(
  private val requestId: Long,
  private val provider: Class<out InlineCompletionProvider>,
  private val invocationTime: Long,
  private val language: Language?,
  private val fileLanguage: Language?,
) {
  private val data = mutableListOf<EventPair<*>>()
  private var computedLogSent = false
  private var computeStartTime = 0L
  private val variantStates = mutableListOf<VariantState>()
  private var typingDuringComputation = 0
  private var variantsNavigationRange = IntRange(0, 0)
  private var switchingVariantsTimes = 0

  // TODO discuss semantics
  fun firstComputed(variantIndex: Int, element: InlineCompletionElement) {
    extendVariantsNumber(variantIndex + 1)

    val state = variantStates[variantIndex]
    if (state.firstComputed) {
      error("First element for $variantIndex index was already computed.")
    }
    state.firstComputed = true
    computeStartTime = System.currentTimeMillis()
    data.add(ComputedEvents.REQUEST_ID.with(requestId))
    data.add(EventFields.Language.with(language))
    data.add(EventFields.CurrentFile.with(fileLanguage))
    data.add(ComputedEvents.TIME_TO_SHOW.with(System.currentTimeMillis() - invocationTime))
    data.add(ComputedEvents.PROVIDER.with(provider))
    nextComputed(variantIndex, element)
    assert(!computedLogSent)
  }

  fun nextComputed(variantIndex: Int, element: InlineCompletionElement) {
    val state = variantStates[variantIndex]
    assert(state.firstComputed) {
      "Call firstShown firstly"
    }
    state.lines += (element.text.lines().size - 1).coerceAtLeast(0)
    if (state.length == 0 && element.text.isNotEmpty()) {
      state.lines++ // first line
    }
    state.length += element.text.length
    assert(!computedLogSent)
  }

  fun truncateTyping(truncateTyping: Int) {
    assert(variantStates.any { it.firstComputed }) // TODO
    typingDuringComputation += truncateTyping
    assert(!computedLogSent)
  }

  fun variantSwitched(fromIndex: Int, toIndex: Int, explicit: Boolean) {
    val minIndex = minOf(fromIndex, toIndex, variantsNavigationRange.first)
    val maxIndex = maxOf(fromIndex, toIndex, variantsNavigationRange.last)
    variantsNavigationRange = IntRange(minIndex, maxIndex)
    if (explicit) {
      switchingVariantsTimes++
    }
  }

  fun selected() {
    finish(FinishType.SELECTED)
  }

  fun canceled(finishType: FinishType) {
    finish(finishType)
  }

  private fun finish(finishType: FinishType) {
    if (computedLogSent) {
      return
    }
    computedLogSent = true
    val fromIndex = minOf(variantsNavigationRange.first, variantStates.size)
    val toIndex = minOf(variantsNavigationRange.last + 1, variantStates.size)
    val interestingVariants = variantStates.subList(fromIndex, toIndex).filter { it.firstComputed }
    data.add(ComputedEvents.LINES.with(interestingVariants.map { it.lines }))
    data.add(ComputedEvents.LENGTH.with(interestingVariants.map { it.length }))
    data.add(ComputedEvents.TYPING_DURING_SHOW.with(typingDuringComputation))
    data.add(ComputedEvents.SHOWING_TIME.with(System.currentTimeMillis() - computeStartTime))
    data.add(ComputedEvents.FINISH_TYPE.with(finishType))
    data.add(ComputedEvents.SWITCHING_VARIANTS_TIMES.with(switchingVariantsTimes))
    InlineCompletionUsageTracker.SHOWN_EVENT.log(data)
  }

  private fun extendVariantsNumber(atLeast: Int) {
    while (variantStates.size < atLeast) {
      variantStates += VariantState(0, 0, false)
    }
  }

  private data class VariantState(
    var length: Int,
    var lines: Int,
    var firstComputed: Boolean
  )
}
