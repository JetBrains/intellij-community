// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionEventBasedSuggestionUpdater
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantsProvider
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionJob
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

class InlineCompletionSession private constructor(
  editor: Editor,
  val provider: InlineCompletionProvider,
  val request: InlineCompletionRequest
) : Disposable {

  private var job: InlineCompletionJob? = null
  private var variantsProvider: InlineCompletionVariantsProvider? = null

  val context = InlineCompletionContext(editor, request.file.language, request.endOffset)

  init {
    Disposer.register(this, context)
  }

  @RequiresEdt
  @RequiresBlockingContext
  @ApiStatus.Experimental
  fun useNextVariant() {
    ThreadingAssertions.assertEventDispatchThread()
    variantsProvider?.useNextVariant()
  }

  @RequiresEdt
  @RequiresBlockingContext
  @ApiStatus.Experimental
  fun usePrevVariant() {
    ThreadingAssertions.assertEventDispatchThread()
    variantsProvider?.usePrevVariant()
  }

  @RequiresEdt
  @ApiStatus.Experimental
  fun getVariantsNumber(): Int? {
    return variantsProvider?.getVariantsNumber()
  }

  @RequiresEdt
  @ApiStatus.Experimental
  fun estimateNonEmptyVariantsNumber(): IntRange? {
    ThreadingAssertions.assertEventDispatchThread()
    return variantsProvider?.estimateNonEmptyVariantsNumber()
  }

  override fun dispose() = Unit

  @RequiresEdt
  internal fun assignJob(job: InlineCompletionJob) {
    ThreadingAssertions.assertEventDispatchThread()
    check(this.job == null) {
      "Inline Completion Session job is already assigned."
    }
    this.job = job
    Disposer.register(this, job)
  }

  @RequiresEdt
  internal fun assignVariants(variantsProvider: InlineCompletionVariantsProvider) {
    ThreadingAssertions.assertEventDispatchThread()
    check(this.variantsProvider == null) {
      "Inline Completion variants provider is already assigned."
    }
    this.variantsProvider = variantsProvider
    Disposer.register(this, variantsProvider)
  }

  @RequiresEdt // TODO very confusing dependencies between VariantsProvider, Session, SessionManager and Handler
  internal fun update(updater: (InlineCompletionSuggestion.VariantSnapshot) -> InlineCompletionEventBasedSuggestionUpdater.UpdateResult) {
    variantsProvider?.update(updater)
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_SESSION = Key.create<InlineCompletionSession>("inline.completion.session")

    @RequiresEdt
    fun getOrNull(editor: Editor): InlineCompletionSession? = editor.getUserData(INLINE_COMPLETION_SESSION)

    @RequiresEdt
    internal fun init(
      editor: Editor,
      provider: InlineCompletionProvider,
      request: InlineCompletionRequest,
      parentDisposable: Disposable
    ): InlineCompletionSession {
      val currentSession = getOrNull(editor)
      check(currentSession == null) { "Inline completion session already exists." }
      return InlineCompletionSession(editor, provider, request).also {
        Disposer.register(parentDisposable, it)
        editor.putUserData(INLINE_COMPLETION_SESSION, it)
      }
    }

    @RequiresEdt
    internal fun remove(editor: Editor) {
      getOrNull(editor)?.apply {
        Disposer.dispose(this)
        editor.putUserData(INLINE_COMPLETION_SESSION, null)
        LOG.trace("Remove inline completion session")
      }
    }
  }
}
