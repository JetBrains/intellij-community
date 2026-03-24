// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogs.INSERTED_STATE_EVENT
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.INSERTED_STATE_EVENT_OLD
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.codeInsight.inline.completion.utils.EditorDisposableCoroutineScope
import com.intellij.codeInsight.inline.completion.utils.storeInUserDataHolderByTheKey
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.text.EditDistance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InsertedStateTracker(private val cs: CoroutineScope) {

  @Deprecated("Replaced with trackV2")
  fun track(requestId: Long,
            language: Language?,
            fileLanguage: Language?,
            editorId: EditorId,
            initialOffset: Int,
            insertOffset: Int,
            finalSuggestion: String,
            durations: List<Duration>) {
    ThreadingAssertions.assertReadAccess()
    val editor = editorId.findEditorOrNull() ?: return
    val actualInitialOffset = minOf(initialOffset, insertOffset)
    val suggestion = editor.document.getText(TextRange(actualInitialOffset, insertOffset)) + finalSuggestion
    val rangeMarker = editor.document.createRangeMarker(actualInitialOffset, actualInitialOffset + suggestion.length)
    getOrCreateEditorScope(editor).launch {
      try {
        coroutineScope {
          durations.forEach { duration ->
            launch {
              delay(duration.toMillis().milliseconds)
              val currentEditor = editorId.findEditorOrNull() ?: return@launch
              if (!currentEditor.isDisposed) {
                val resultText = readAction { if (rangeMarker.isValid) rangeMarker.document.getText(rangeMarker.textRange) else "" }
                val commonPrefixLength = resultText.commonPrefixWith(suggestion).length
                val commonSuffixLength = resultText.commonSuffixWith(suggestion).length
                val editDistance = EditDistance.optimalAlignment(suggestion, resultText, true)
                val editDistanceNoAdd = editDistance - maxOf(resultText.length - suggestion.length, 0)
                val data = mutableListOf<EventPair<*>>(
                  InlineCompletionUsageTracker.ShownEvents.REQUEST_ID.with(requestId),
                  EventFields.DurationMs.with(duration.toMillis()),
                  InlineCompletionUsageTracker.InsertedStateEvents.SUGGESTION_LENGTH.with(suggestion.length),
                  InlineCompletionUsageTracker.InsertedStateEvents.RESULT_LENGTH.with(resultText.length),
                  InlineCompletionUsageTracker.InsertedStateEvents.EDIT_DISTANCE.with(editDistance),
                  InlineCompletionUsageTracker.InsertedStateEvents.EDIT_DISTANCE_NO_ADD.with(editDistanceNoAdd),
                  InlineCompletionUsageTracker.InsertedStateEvents.COMMON_PREFIX_LENGTH.with(commonPrefixLength),
                  InlineCompletionUsageTracker.InsertedStateEvents.COMMON_SUFFIX_LENGTH.with(commonSuffixLength),
                )
                language?.let { data.add(EventFields.Language.with(it)) }
                fileLanguage?.let { data.add(EventFields.Language.with(it)) }
                INSERTED_STATE_EVENT_OLD.log(data)
              }
            }
          }
        }
      }
      finally {
        rangeMarker.dispose()
      }
    }
  }

  fun trackV2(requestId: Long,
            fileLanguage: Language?,
            editorId: EditorId,
            initialOffset: Int,
            insertOffset: Int,
            finalSuggestion: String,
            durations: List<Duration>) {
    ThreadingAssertions.assertReadAccess()
    val editor = editorId.findEditorOrNull() ?: return
    val actualInitialOffset = minOf(initialOffset, insertOffset)
    val suggestion = editor.document.getText(TextRange(actualInitialOffset, insertOffset)) + finalSuggestion
    val rangeMarker = editor.document.createRangeMarker(actualInitialOffset, minOf(actualInitialOffset + suggestion. length, editor.document.textLength))
    getOrCreateEditorScope(editor).launch {
      try {
        coroutineScope {
          durations.forEach { duration ->
            launch {
              delay(duration.toMillis().milliseconds)
              val currentEditor = editorId.findEditorOrNull() ?: return@launch
              if (currentEditor.isDisposed) {
                return@launch
              }
              readAction {
                val resultText = if (rangeMarker.isValid) rangeMarker.document.getText(rangeMarker.textRange) else ""
                val commonPrefixLength = resultText.commonPrefixWith(suggestion).length
                val commonSuffixLength = resultText.commonSuffixWith(suggestion).length
                val editDistance = EditDistance.optimalAlignment(suggestion, resultText, true)
                val editDistanceNoAdd = editDistance - maxOf(resultText.length - suggestion.length, 0)
                val data = mutableListOf<EventPair<*>>(
                  InlineCompletionLogs.InsertedStateEvents.REQUEST_ID.with(requestId),
                  EventFields.DurationMs.with(duration.toMillis()),
                  InlineCompletionLogs.InsertedStateEvents.SUGGESTION_LENGTH.with(suggestion.length),
                  InlineCompletionLogs.InsertedStateEvents.RESULT_LENGTH.with(resultText.length),
                  InlineCompletionLogs.InsertedStateEvents.EDIT_DISTANCE.with(editDistance),
                  InlineCompletionLogs.InsertedStateEvents.EDIT_DISTANCE_NO_ADD.with(editDistanceNoAdd),
                  InlineCompletionLogs.InsertedStateEvents.COMMON_PREFIX_LENGTH.with(commonPrefixLength),
                  InlineCompletionLogs.InsertedStateEvents.COMMON_SUFFIX_LENGTH.with(commonSuffixLength),
                )
                fileLanguage?.let { data.add(EventFields.Language.with(it)) }
                INSERTED_STATE_EVENT.log(data)
              }
            }
          }
        }
      }
      finally {
        rangeMarker.dispose()
      }
    }
  }

  /**
   * Reuses one editor-bound child scope for all delayed tracking jobs.
   *
   * The scope wrapper is stored in editor user data and canceled on editor disposal,
   * so service-level coroutine scope does not retain editor references.
   */
  private fun getOrCreateEditorScope(editor: Editor): CoroutineScope {
    return editor.storeInUserDataHolderByTheKey(EDITOR_TRACKER_SCOPE_KEY) {
      EditorDisposableCoroutineScope(cs)
    }.scope
  }

  private companion object {
    private val EDITOR_TRACKER_SCOPE_KEY = Key.create<EditorDisposableCoroutineScope>("inline.completion.inserted.state.tracker.scope")
  }
}
