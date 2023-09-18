// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

internal data class InlineCompletionSession(val context: InlineCompletionContext, val provider: InlineCompletionProvider) {

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_SESSION = Key.create<InlineCompletionSession>("inline.completion.session")

    internal fun getOrNull(editor: Editor): InlineCompletionSession? = editor.getUserData(INLINE_COMPLETION_SESSION)

    internal fun getOrInit(editor: Editor, provider: InlineCompletionProvider): InlineCompletionSession {
      val currentSession = getOrNull(editor)
      if (currentSession != null && currentSession.provider == provider) {
        return currentSession
      }
      return InlineCompletionSession(InlineCompletionContext(editor), provider).also {
        editor.putUserData(INLINE_COMPLETION_SESSION, it)
      }
    }

    internal fun remove(editor: Editor) {
      getOrNull(editor)?.context?.clear()
      editor.putUserData(INLINE_COMPLETION_SESSION, null)
      LOG.trace("Remove inline completion session")
    }
  }
}
