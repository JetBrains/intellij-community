// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionJob
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Experimental
class InlineCompletionSession private constructor(
  val context: InlineCompletionContext,
  val provider: InlineCompletionProvider
) : Disposable {

  init {
    Disposer.register(this, context)
  }

  private val myJob = AtomicReference<InlineCompletionJob?>()

  internal val job: InlineCompletionJob?
    get() = myJob.get()

  internal fun assignJob(job: InlineCompletionJob) {
    val currentJob = myJob.getAndSet(job)
    check(currentJob == null) { "Job is already assigned to a session." }
    Disposer.register(this, job)
  }

  override fun dispose() = Unit

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_SESSION = Key.create<InlineCompletionSession>("inline.completion.session")

    @RequiresEdt
    fun getOrNull(editor: Editor): InlineCompletionSession? = editor.getUserData(INLINE_COMPLETION_SESSION)

    @RequiresEdt
    internal fun init(
      editor: Editor,
      provider: InlineCompletionProvider,
      parentDisposable: Disposable
    ): InlineCompletionSession {
      val currentSession = getOrNull(editor)
      check(currentSession == null) { "Inline completion session already exists." }
      return InlineCompletionSession(InlineCompletionContext(editor), provider).also {
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
