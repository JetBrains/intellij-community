// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.ui.components.panels.Wrapper

internal class EditorHandlerPanel private constructor(
  private val editorFactory: EditorFactory,
  private val editorSupplier: (EditorFactory) -> Editor
) : Wrapper() {
  private var editor: Editor? = null

  override fun addNotify() {
    super.addNotify();
    createAndSetNewEditor()
  }

  private fun createAndSetNewEditor() {
    clearAndReleaseEditor()

    editor = editorSupplier(editorFactory)
    setContent(editor?.component!!)
  }

  private fun clearAndReleaseEditor() {
    editor?.let { editorFactory.releaseEditor(it) }

    setContent(null)
    editor = null
  }

  override fun removeNotify() {
    super.removeNotify()
    clearAndReleaseEditor()
  }

  companion object {
    fun create(editorFactory: EditorFactory, editorSupplier: (EditorFactory) -> Editor) = EditorHandlerPanel(editorFactory, editorSupplier)
  }
}