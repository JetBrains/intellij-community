// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.InvokedEvents
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilCore

/**
 * This tracker lives from the moment the inline completion is invoked until the end of generation.
 * This tracker is not thread-safe.
 */
internal class InlineCompletionInvocationTracker(
  private val invocationTime: Long,
  private val request: InlineCompletionRequest,
  private val provider: Class<out InlineCompletionProvider>,
  private val requestId: Long,
) {
  constructor(event: InlineCompletionEventType.Request) : this(event.lastInvocation, event.request, event.provider, event.requestId)

  private var finished = false
  private val data = mutableListOf<EventPair<*>>()
  private var hasSuggestions: Boolean? = null
  private var canceled: Boolean = false
  private var exception: Boolean = false
  private var language: Language? = null
  private var fileLanguage: Language? = null

  fun createShowTracker() = InlineCompletionShowTracker(
    request,
    requestId,
    provider,
    invocationTime,
    language,
    fileLanguage,
  )

  fun captureContext(editor: Editor, offset: Int) {
    val psiFile = PsiDocumentManager.getInstance(editor.project ?: return).getPsiFile(editor.document) ?: return
    language = PsiUtilCore.getLanguageAtOffset(psiFile, offset)
    fileLanguage = psiFile.language
    data.add(EventFields.Language.with(language))
    data.add(EventFields.CurrentFile.with(fileLanguage))
    assert(!finished)
  }

  fun noSuggestions() {
    hasSuggestions = false
    assert(!finished)
  }

  fun hasSuggestions() {
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

    buildList {
      val descriptor = InlineCompletionProviderSpecificUsageData.InvocationDescriptor(request.editor, request.file)
      InlineCompletionProviderSpecificUsageData.EP_NAME.forEachExtensionSafe {
        if (getPluginInfo(it.javaClass).isSafeToReport() && !it.skipLogging()) {
          addAll(it.getAdditionalInvocationUsageData(descriptor))
        }
      }
    }.takeIf { it.isNotEmpty() }?.let {
      data.add(InvokedEvents.ADDITIONAL.with(ObjectEventData(it)))
    }

    InlineCompletionUsageTracker.INVOKED_EVENT.log(request.editor.project, listOf(
      InvokedEvents.REQUEST_ID.with(requestId),
      *data.toTypedArray(),
      InvokedEvents.EVENT.with(request.event::class.java),
      InvokedEvents.PROVIDER.with(provider),
      InvokedEvents.PROVIDER_PLUGIN_INFO.with(getPluginInfo(provider)),
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

  companion object {
    val LOG = thisLogger()
  }
}