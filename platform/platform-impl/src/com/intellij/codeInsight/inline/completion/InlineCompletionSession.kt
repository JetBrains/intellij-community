// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.atomic.AtomicReference

internal class InlineCompletionSession private constructor(
  val context: InlineCompletionContext,
  val provider: InlineCompletionProvider
) {

  private var toDispose: (() -> Unit)? = null
  private val myJob = AtomicReference<InlineCompletionJob?>()

  val job: InlineCompletionJob?
    get() = myJob.get()

  fun assignJob(job: InlineCompletionJob) {
    val currentJob = myJob.getAndSet(job)
    check(currentJob == null) { "Job is already assigned to a session." }
  }

  @RequiresEdt
  fun whenDisposed(block: () -> Unit) {
    // TODO change semantics after starting truly listening to typing events: we shouldn't replace dispose, we need to collect them
    toDispose?.invoke()
    toDispose = block
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_SESSION = Key.create<InlineCompletionSession>("inline.completion.session")

    @RequiresEdt
    fun getOrNull(editor: Editor): InlineCompletionSession? = editor.getUserData(INLINE_COMPLETION_SESSION)

    @RequiresEdt
    fun init(editor: Editor, provider: InlineCompletionProvider): InlineCompletionSession {
      val currentSession = getOrNull(editor)
      check(currentSession == null) { "Inline completion session already exists." }
      return InlineCompletionSession(InlineCompletionContext(editor), provider).also {
        editor.putUserData(INLINE_COMPLETION_SESSION, it)
      }
    }

    @RequiresEdt
    fun remove(editor: Editor) {
      val currentSession = getOrNull(editor)?.apply {
        toDispose?.invoke()
        context.clear()
        context.invalidate()
        job?.cancel()
      }

      if (currentSession != null) {
        editor.putUserData(INLINE_COMPLETION_SESSION, null)
        LOG.trace("Remove inline completion session")
      }
    }
  }
}
