// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData

/**
 * It is a special editor disposable which will be disposed BEFORE the editor itself.
 */
class JupyterBeforeEditorDisposable(editor: Editor) : Disposable.Default {
  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        if (event.editor != editor) return
        Disposer.dispose(this@JupyterBeforeEditorDisposable)
      }
    }, this)
  }

  companion object {
    private val JUPYTER_EDITOR_DISPOSE_KEY = Key.create<JupyterBeforeEditorDisposable>("JupyterEditorDispose")

    fun create(editor: Editor): JupyterBeforeEditorDisposable {
      val editorDispose = JupyterBeforeEditorDisposable(editor)
      EditorUtil.disposeWithEditor(editor, editorDispose)
      editor.putUserData(JUPYTER_EDITOR_DISPOSE_KEY, editorDispose)
      Disposer.register(editorDispose) { editor.removeUserData(JUPYTER_EDITOR_DISPOSE_KEY) }
      return editorDispose
    }

    fun get(editor: Editor): JupyterBeforeEditorDisposable = editor.getUserData(JUPYTER_EDITOR_DISPOSE_KEY)!!
  }
}