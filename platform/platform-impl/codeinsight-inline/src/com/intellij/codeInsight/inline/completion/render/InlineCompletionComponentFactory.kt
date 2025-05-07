// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key

internal abstract class InlineCompletionComponentFactory<T : Disposable> {

  protected abstract val key: Key<T>

  protected abstract fun create(editor: Editor): T

  fun get(editor: Editor): T = editor.getUserData(key) ?: doCreate(editor)

  private fun doCreate(editor: Editor): T {
    if (editor.isDisposed) {
      LOG.error("[Inline Completion] Editor is disposed. Cannot acquire '${this::class.simpleName}' for $editor.")
    }

    return create(editor).also {
      EditorUtil.disposeWithEditor(editor) {
        editor.putUserData(key, null)
        Disposer.dispose(it)
      }
      editor.putUserData(key, it)
    }
  }

  companion object {
    private val LOG = thisLogger()
  }
}