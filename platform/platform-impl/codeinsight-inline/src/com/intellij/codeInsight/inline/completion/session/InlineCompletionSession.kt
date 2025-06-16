// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantsProvider
import com.intellij.codeInsight.inline.edit.InlineEditRequestJob
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * This class defines the lifecycle and rules for a single or multiple instances of inline code completion.
 * It works in conjunction with an [InlineCompletionProvider] and an [InlineCompletionRequest], which define
 * the source of completion suggestions and the context of the request respectively.
 *
 * An [InlineCompletionSession] is designed to manage the inline completion jobs and variants providers.
 * It provides ability to navigate through the completion variants, capture the state of these variants,
 * and verify the active status of the session.
 */
class InlineCompletionSession private constructor(
  val editor: Editor,
  val provider: InlineCompletionProvider,
  val request: InlineCompletionRequest
) : Disposable {

  private var job: InlineEditRequestJob? = null
  private var variantsProvider: InlineCompletionVariantsProvider? = null

  val context = InlineCompletionContext(editor, request.file.language, request.endOffset)

  init {
    Disposer.register(this, context)
  }

  /**
   * Checks whether the inline completion session is currently active.
   *
   * An inline completion session is considered active if it has an associated
   * variants from [provider] and [context] in which it is running has not yet been disposed.
   */
  @RequiresEdt
  fun isActive(): Boolean {
    return variantsProvider != null && !context.isDisposed
  }

  /**
   * The `capture` method is designed to capture the current state of the inline completion session.
   *
   * It aims to capture a snapshot of the inline code completion variants at any given point in time during the session.
   * The captured information enables us to retrospectively analyze the state of the inline completion session,
   * specifically around different completion variants provided. For thread-safety reasons, this method must be called on EDT.
   *
   * Invoking this method results in the capture of the variants from the associated [provider].
   * If no variants are currently provided, this method will return `null`, indicating that capturing the session state was not possible.
   *
   * @return If a capture was successful, an instance of the [Snapshot] class containing a snapshot of completion variants is returned.
   *         However, if no variants are provided at the moment, the method will return `null`.
   */
  @RequiresEdt
  fun capture(): Snapshot? {
    ThreadingAssertions.assertEventDispatchThread()
    val variants = variantsProvider?.captureVariants() ?: return null
    return Snapshot(variants)
  }

  /**
   * The method navigates forward through a list of inline completion variants from [provider].
   *
   * This method shifts the current focus to the next available variant in the list of inline completion variants.
   * If it reaches the end of the list, the selection cycles back to the start.
   *
   * The method can select a variant as long as it contains at least one computed element. Otherwise, the variant is skipped.
   */
  @RequiresEdt
  fun useNextVariant() {
    ThreadingAssertions.assertEventDispatchThread()
    variantsProvider?.useNextVariant()
  }

  /**
   * The method navigates backward through a list of inline completion variants from [provider].
   *
   * This method shifts the current focus to the previous available variant in the list of inline completion variants.
   * If it reaches the start of the list, the selection cycles back to the end.
   *
   * The method can select a variant as long as it contains at least one computed element. Otherwise, the variant is skipped.
   */
  @RequiresEdt
  fun usePrevVariant() {
    ThreadingAssertions.assertEventDispatchThread()
    variantsProvider?.usePrevVariant()
  }

  override fun dispose() = Unit

  @RequiresEdt
  internal fun assignJob(job: InlineEditRequestJob) {
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

  @RequiresEdt
  internal fun update(
    event: InlineCompletionEvent,
    updater: (InlineCompletionVariant.Snapshot) -> InlineCompletionSuggestionUpdateManager.UpdateResult
  ): Boolean {
    check(isActive())
    return checkNotNull(variantsProvider).update(event, updater)
  }

  /**
   * Represents information of all the variants, provided by [provider], if [provider] suggested something at that moment.
   */
  class Snapshot @ApiStatus.Internal constructor(val variants: List<InlineCompletionVariant.Snapshot>) {

    /**
     * Information about the variant currently seen by a user.
     */
    val activeVariant: InlineCompletionVariant.Snapshot = variants.first { it.isActive }

    /**
     * Total number of the variants provided by [provider].
     */
    val variantsNumber: Int
      get() = variants.size

    /**
     * Estimates the number of possibly non-empty variants. A variant is considered as empty if it contains no elements.
     *
     * The first value in the range denotes the number of already non-empty variants.
     *
     * The second value in the range denotes the number of possibly non-empty variants
     * (the variants that are already non-empty plus the variants that can be non-empty in the future).
     */
    @ApiStatus.Experimental
    val nonEmptyVariantsRange: IntRange = run {
      val forSure = variants.count { !it.isEmpty() }
      val potential = variants.count {
        !it.isEmpty()
        || it.state == InlineCompletionVariant.Snapshot.State.IN_PROGRESS
        || it.state == InlineCompletionVariant.Snapshot.State.UNTOUCHED
      }
      IntRange(forSure, potential)
    }
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_SESSION = Key.create<InlineCompletionSession>("inline.completion.session")

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
        if (!context.isDisposed) {
          Disposer.dispose(this)
          LOG.trace("[Inline Completion] Remove inline completion session")
        }
        else {
          LOG.warn("[Inline Completion] Cannot dispose session because it's already disposed.")
        }
        editor.putUserData(INLINE_COMPLETION_SESSION, null)
      }
    }
  }
}
