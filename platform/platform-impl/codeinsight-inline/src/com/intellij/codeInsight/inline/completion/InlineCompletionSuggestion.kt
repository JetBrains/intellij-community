// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

/**
 * Abstract class representing an inline completion suggestion.
 *
 * Provides the suggestion flow for generating **only one suggestion**.
 * @see InlineCompletionElement
 */
@Deprecated(
  message = "Use InlineCompletionSingleSuggestion",
  replaceWith = ReplaceWith("InlineCompletionSingleSuggestion"),
  level = DeprecationLevel.WARNING
)
@ScheduledForRemoval
abstract class InlineCompletionSuggestion : UserDataHolderBase(), InlineCompletionSingleSuggestion {

  abstract val suggestionFlow: Flow<InlineCompletionElement>

  final override suspend fun getVariant(): InlineCompletionVariant {
    return InlineCompletionVariant.build(this, suggestionFlow)
  }

  @Deprecated(
    message = "Use InlineCompletionSingleSuggestion.build",
    replaceWith = ReplaceWith("InlineCompletionSingleSuggestion.build(elements = suggestionFlow)"),
    level = DeprecationLevel.WARNING
  )
  @ScheduledForRemoval
  class Default(override val suggestionFlow: Flow<InlineCompletionElement>) : InlineCompletionSuggestion()

  companion object {

    @Deprecated(
      message = "Use com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion.Empty",
      replaceWith = ReplaceWith(
        "com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion.Empty",
        "com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion"
      ),
      level = DeprecationLevel.WARNING
    )
    @ScheduledForRemoval
    fun empty(): InlineCompletionSuggestion = Default(emptyFlow())

    @Deprecated(
      message = "Use InlineCompletionSingleSuggestion.build",
      replaceWith = ReplaceWith("InlineCompletionSingleSuggestion.build(buildElements = buildSuggestion)"),
      level = DeprecationLevel.WARNING
    )
    @ScheduledForRemoval
    fun withFlow(buildSuggestion: suspend FlowCollector<InlineCompletionElement>.() -> Unit): InlineCompletionSuggestion {
      return Default(flow(buildSuggestion))
    }
  }
}
