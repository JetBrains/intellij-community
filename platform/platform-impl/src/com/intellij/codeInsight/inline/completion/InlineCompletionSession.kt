// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

internal class InlineCompletionSession private constructor(val context: InlineCompletionContext, val provider: InlineCompletionProvider) {

  var job: InlineCompletionJob? = null
    private set

  fun assignJob(job: InlineCompletionJob) {
    check(this.job == null) { "Job is already assigned to a session." }
    this.job = job
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
