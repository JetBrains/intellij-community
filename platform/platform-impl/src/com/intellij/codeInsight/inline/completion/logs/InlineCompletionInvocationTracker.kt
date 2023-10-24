// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import kotlin.random.Random

/**
 * This tracker lives from the moment the inline completion is invoked until the end of generation.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionInvocationTracker(
  private val invocationTime: Long,
  private val request: InlineCompletionRequest,
  private val provider: Class<out InlineCompletionProvider>
) {
  constructor(event: InlineCompletionEventType.Request) : this(event.lastInvocation, event.request, event.provider)

  val requestId = Random.nextLong()
  private var finished = false
  private val data = mutableListOf<EventPair<*>>()
  private val contextFeatures = ContainerUtil.createConcurrentList<EventPair<*>>()
  private var hasSuggestions: Boolean? = null
  private var canceled: Boolean = false
  private var exception: Boolean = false
  private var language: Language? = null
  private var fileLanguage: Language? = null

  fun createShowTracker() = InlineCompletionShowTracker(
    requestId,
    invocationTime,
    InlineContextFeatures.getEventPair(contextFeatures),
    language,
    fileLanguage,
  )

  fun captureContext(editor: Editor, offset: Int) {
    val psiFile = PsiDocumentManager.getInstance(editor.project ?: return).getPsiFile(editor.document) ?: return
    language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
    fileLanguage = psiFile.language
    data.add(EventFields.Language.with(language))
    data.add(EventFields.CurrentFile.with(fileLanguage))
    logger<InlineCompletionUsageTracker>()
    InlineContextFeatures.capture(editor, offset, contextFeatures)
    assert(!finished)
  }

  fun noSuggestions() {
    hasSuggestions = false
    assert(!finished)
  }

  fun hasSuggestion() {
    hasSuggestions = true
    assert(!finished)
  }

  fun canceled() {
    canceled = true
    assert(!finished)
  }

  fun exception() {
    exception = true
    assert(!finished)
  }

  fun finished() {
    if (finished) {
      error("Already finished")
    }
    finished = true
    invokedEvent.log(listOf(
      InvokedEvents.REQUEST_ID.with(requestId),
      *data.toTypedArray(),
      InvokedEvents.EVENT.with(request.event::class.java),
      InvokedEvents.PROVIDER.with(provider),
      InvokedEvents.TIME_TO_COMPUTE.with(System.currentTimeMillis() - invocationTime),
      InvokedEvents.OUTCOME.with(
        when {
          // fixed order
          exception -> InvokedEvents.Outcome.EXCEPTION
          canceled -> InvokedEvents.Outcome.CANCELED
          hasSuggestions == true -> InvokedEvents.Outcome.SHOW
          hasSuggestions == false -> InvokedEvents.Outcome.NO_SUGGESTIONS
          else -> null
        }
      )
    ))
  }

  private object InvokedEvents {
    val REQUEST_ID = EventFields.Long("request_id")
    val EVENT = EventFields.Class("event")
    val PROVIDER = EventFields.Class("provider")
    val TIME_TO_COMPUTE = EventFields.Long("time_to_compute")
    val OUTCOME = EventFields.NullableEnum<Outcome>("outcome")

    enum class Outcome {
      EXCEPTION,
      CANCELED,
      SHOW,
      NO_SUGGESTIONS
    }
  }

  private val invokedEvent: VarargEventId = InlineCompletionUsageTracker.GROUP.registerVarargEvent(
    "invoked",
    InvokedEvents.REQUEST_ID,
    EventFields.Language,
    EventFields.CurrentFile,
    InvokedEvents.EVENT,
    InvokedEvents.PROVIDER,
    InvokedEvents.TIME_TO_COMPUTE,
    InvokedEvents.OUTCOME,
  )
}