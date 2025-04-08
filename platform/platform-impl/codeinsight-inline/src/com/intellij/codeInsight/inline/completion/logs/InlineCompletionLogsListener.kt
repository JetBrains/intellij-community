// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.editor.InlineCompletionEditorType
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FINAL_PROPOSAL_LENGTH
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FINAL_PROPOSAL_LINE
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FINISH_TYPE
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.FULL_INSERT_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.INVALIDATION_EVENT
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.NEXT_LINE_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.NEXT_WORD_ACTIONS
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.RECEIVED_PROPOSAL_LENGTH
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.RECEIVED_PROPOSAL_LINES
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.SHOWING_TIME
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TIME_TO_START_SHOWING
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TOTAL_INSERTED_LENGTH
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.TOTAL_INSERTED_LINES
import com.intellij.codeInsight.inline.completion.logs.FinishingLogs.WAS_SHOWN
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsUtils.isLoggable
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.CARET_LANGUAGE
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.EDITOR_TYPE
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.FILE_LANGUAGE
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.INLINE_API_PROVIDER
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.REQUEST_EVENT
import com.intellij.codeInsight.inline.completion.logs.StartingLogs.REQUEST_ID
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionInvalidationListener
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiUtilCore
import java.time.Duration

internal class InlineCompletionLogsListener(private val editor: Editor) : InlineCompletionFilteringEventListener(),
                                                                          InlineCompletionInvalidationListener {
  /**
   * This field is not thread-safe, please access it only on EDT.
   */
  private var holder = Holder()

  /**
   * Fields inside [Holder] are not thread-safe, please access them only on EDT.
   */
  private class Holder() {
    var requestId: Long = 0
    var lastInvocationTimestamp: Long = 0
    var showStartTime: Long = 0
    var wasShown: Boolean = false
    var fullInsertActions: Int = 0
    var nextWordActions: Int = 0
    var nextLineActions: Int = 0
    var totalInsertedLength: Int = 0
    var totalInsertedLines: Int = 0
    var potentiallySelectedIndex: Int? = null
    val variantStates = mutableMapOf<Int, VariantState>()
    var trackedStartOffset: Int? = null
    var trackedEndOffset: Int? = null
    var trackedLanguage: Language? = null
  }

  override fun isApplicable(requestEvent: InlineCompletionEventType.Request): Boolean {
    return requestEvent.provider.isLoggable()
  }

  override fun onRequest(event: InlineCompletionEventType.Request) {
    holder = Holder()
    holder.lastInvocationTimestamp = System.currentTimeMillis()
    holder.requestId = event.request.requestId

    val container = InlineCompletionLogsContainer.create(event.request.editor)
    container.addProject(event.request.editor.project)
    container.add(REQUEST_ID with event.request.requestId)
    container.add(REQUEST_EVENT with event.request.event.javaClass)
    container.add(EDITOR_TYPE with InlineCompletionEditorType.get(event.request.editor))
    container.add(INLINE_API_PROVIDER with event.provider)
    val file = event.request.event.toRequest()?.file
    file?.let {
      val caretLanguage = PsiUtilCore.getLanguageAtOffset(it, event.request.endOffset)
      val fileLanguage = it.language
      container.add(FILE_LANGUAGE with fileLanguage)
      if (caretLanguage != fileLanguage) {
        container.add(CARET_LANGUAGE with caretLanguage)
      }
    }
    container.addAsync {
      readAction {
        InlineCompletionContextLogs.getFor(event.request)
      }
    }
  }

  override fun onComputed(event: InlineCompletionEventType.Computed) {
    if (holder.potentiallySelectedIndex == null) {
      holder.potentiallySelectedIndex = event.variantIndex // It's the first variant that will be shown to a user
    }
    val state = holder.variantStates.computeIfAbsent(event.variantIndex) { VariantState() }
    state.initialSuggestion += event.element.text
    state.finalSuggestion += event.element.text
  }

  override fun onShow(event: InlineCompletionEventType.Show) {
    if (holder.wasShown) return
    holder.wasShown = true
    val container = InlineCompletionLogsContainer.get(editor) ?: return
    container.add(TIME_TO_START_SHOWING with (System.currentTimeMillis() - holder.lastInvocationTimestamp))
    holder.showStartTime = System.currentTimeMillis()
  }

  override fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {
    holder.potentiallySelectedIndex = event.toVariantIndex
  }

  override fun onInsert(event: InlineCompletionEventType.Insert) {
    val context = InlineCompletionContext.getOrNull(editor)
    val textToInsert = context?.textToInsert() ?: return
    holder.totalInsertedLength += textToInsert.length
    holder.totalInsertedLines += textToInsert.lines().size
    holder.fullInsertActions++
    holder.trackedStartOffset = context.startOffset()
    holder.trackedEndOffset = context.endOffset()
    holder.trackedLanguage = context.language
  }

  override fun onAfterInsert(event: InlineCompletionEventType.AfterInsert) {
    startTracking()
    // we can clean up now
    holder = Holder()
  }

  override fun onChange(event: InlineCompletionEventType.Change) {
    when (event.event) {
      is InlineCompletionEvent.InsertNextWord -> {
        holder.totalInsertedLength += event.lengthChange
        holder.nextWordActions++
      }
      is InlineCompletionEvent.InsertNextLine -> {
        holder.totalInsertedLength += event.lengthChange
        holder.totalInsertedLines++
        holder.nextLineActions++
      }
    }
    val state = holder.variantStates[event.variantIndex]!!
    state.finalSuggestion = event.elements.joinToString("") { it.text }
  }

  private fun onInvalidatedByEvent(eventClass: Class<out InlineCompletionEvent>) {
    val container = InlineCompletionLogsContainer.get(editor) ?: return
    container.add(INVALIDATION_EVENT.with(eventClass))
  }

  override fun onInvalidatedByEvent(event: InlineCompletionEvent) {
    onInvalidatedByEvent(event.javaClass)
  }

  override fun onInvalidatedByUnclassifiedDocumentChange() {
    onInvalidatedByEvent(InlineCompletionEvent.DocumentChange::class.java)
  }

  override fun onHide(event: InlineCompletionEventType.Hide) {
    val container = InlineCompletionLogsContainer.remove(editor) ?: return
    with(holder) {
      container.add(FINISH_TYPE with event.finishType)
      container.add(WAS_SHOWN with wasShown)
      if (wasShown) {
        container.add(SHOWING_TIME.with(System.currentTimeMillis() - showStartTime))
        container.add(FULL_INSERT_ACTIONS with fullInsertActions)
        container.add(NEXT_WORD_ACTIONS with nextWordActions)
        container.add(NEXT_LINE_ACTIONS with nextLineActions)
        container.add(TOTAL_INSERTED_LENGTH with totalInsertedLength)
        container.add(TOTAL_INSERTED_LINES with totalInsertedLines)
        variantStates[potentiallySelectedIndex]?.let { state ->
          container.add(RECEIVED_PROPOSAL_LENGTH with state.initialSuggestion.length)
          container.add(RECEIVED_PROPOSAL_LINES with state.initialSuggestion.lines().size)
          container.add(FINAL_PROPOSAL_LENGTH with state.finalSuggestion.length)
          container.add(FINAL_PROPOSAL_LINE with state.finalSuggestion.lines().size)
        }
      }
    }
    container.logCurrent() // see doc of this function, it's very fast, and we should wait for its completion

    // `SELECTED` case is handled in the afterInsert case
    if (event.finishType != InlineCompletionUsageTracker.ShownEvents.FinishType.SELECTED) {
      holder = Holder()
    }
  }

  private class VariantState {
    var initialSuggestion: String = ""
    var finalSuggestion: String = ""
  }

  private fun startTracking() {
    val selectedIndex = holder.potentiallySelectedIndex ?: return
    val selectedVariant = holder.variantStates[selectedIndex] ?: return
    val insertOffset = holder.trackedStartOffset ?: return
    val endOffset = holder.trackedEndOffset ?: return
    service<InsertedStateTracker>().trackV2(
      holder.requestId,
      holder.trackedLanguage,
      editor,
      endOffset,
      insertOffset,
      selectedVariant.finalSuggestion,
      getDurations(),
    )
  }

  private fun getDurations(): List<Duration> =
    if (ApplicationManager.getApplication().isUnitTestMode) {
      listOf(Duration.ofMillis(TEST_CHECK_STATE_AFTER_MLS))
    }
    else {
      listOf(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5))
    }
}

private object StartingLogs : PhasedLogs(Phase.INLINE_API_STARTING) {
  val REQUEST_ID = registerBasic(EventFields.Long("request_id", "Unique request id for the inline completion session"))
  val REQUEST_EVENT = register(EventFields.Class("request_event", "Type of the event that caused the request for the inline completion session"))
  val EDITOR_TYPE = registerBasic(EventFields.Enum<InlineCompletionEditorType>("editor_type", "Type of the editor"))
  val INLINE_API_PROVIDER = registerBasic(EventFields.Class("inline_api_provider", "Type of the inline completion provider that was used for the request"))
  val FILE_LANGUAGE = registerBasic(EventFields.Language("file_language", "Language of the file that was opened for the request"))
  val CARET_LANGUAGE = register(EventFields.Language("caret_language", "Language at the caret position"))
}

private object FinishingLogs : PhasedLogs(Phase.INLINE_API_FINISHING) {
  val WAS_SHOWN = registerBasic(EventFields.Boolean("was_shown", "Indicates whether completion or some part of it was shown during the session or not"))
  val TIME_TO_START_SHOWING = registerBasic(EventFields.Long("time_to_start_showing", "Time from the completion request to start showing at least one element"))
  val SHOWING_TIME = registerBasic(EventFields.Long("showing_time", "Duration from the beginning of the show to its end (for any reason)"))
  val FINISH_TYPE = registerBasic(EventFields.Enum("finish_type", InlineCompletionUsageTracker.ShownEvents.FinishType::class.java, "Indicates how completion session was finished"))
  val INVALIDATION_EVENT = registerBasic(EventFields.Class("invalidation_event", "In case of finish type 'invalidated'  which exactly event invalidated the completion"))
  val FULL_INSERT_ACTIONS = registerBasic(EventFields.Int("full_insert_actions", "Number of full inline completion inserts"))
  val NEXT_WORD_ACTIONS = registerBasic(EventFields.Int("next_word_actions", "Number of next word inline completion inserts"))
  val NEXT_LINE_ACTIONS = registerBasic(EventFields.Int("next_line_actions", "Number of next line inline completion inserts"))
  val TOTAL_INSERTED_LENGTH = registerBasic(EventFields.Int("total_inserted_length", "Total length of inserted text"))
  val TOTAL_INSERTED_LINES = registerBasic(EventFields.Int("total_inserted_lines", "Total number of inserted lines"))
  val RECEIVED_PROPOSAL_LENGTH = registerBasic(EventFields.Int("received_proposal_length", "Length of proposal that was received from the inline completion provider"))
  val RECEIVED_PROPOSAL_LINES = registerBasic(EventFields.Int("received_proposal_lines", "Number of lines in proposal that was received from the inline completion provider"))
  val FINAL_PROPOSAL_LENGTH = registerBasic(EventFields.Int("final_proposal_length", "Length of proposal at finish"))
  val FINAL_PROPOSAL_LINE = registerBasic(EventFields.Int("final_proposal_line", "Number of lines in proposal at finish"))
}

internal class InlineCompletionListenerSessionLogs : InlineCompletionSessionLogsEP {
  override val logGroups = listOf(StartingLogs, FinishingLogs)
}