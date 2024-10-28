// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.editor.actions.command.mode

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.ui.Gray
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import kotlinx.serialization.Serializable

/**
 * The Jupyter Notebook has a modal user interface.
 * This means that the keyboard does different things depending on which mode the Notebook is in.
 * There are two modes: edit mode and command mode.
 *
 * @see <a href="https://jupyter-notebook.readthedocs.io/en/stable/examples/Notebook/Notebook%20Basics.html#Modal-editor">
 *   Notebook Modal Editor
 *   </a>
 */
@Serializable
enum class NotebookEditorMode {
  EDIT,
  COMMAND
}

val NOTEBOOK_EDITOR_MODE: Topic<NotebookEditorModeListener> = Topic.create("Notebook Editor Mode",
                                                                           NotebookEditorModeListener::class.java)

@FunctionalInterface
interface NotebookEditorModeListener {
  fun onModeChange(editor: Editor, mode: NotebookEditorMode)
}

private val key = Key<NotebookEditorMode>("Jupyter Notebook Editor Mode")

val Editor.currentMode: NotebookEditorMode
  get() {
    return getEditor().getUserData(key) ?: NotebookEditorMode.COMMAND
  }

private fun Editor.getEditor(): Editor {
  var editor = this
  while (editor is EditorWindow) {
    editor = editor.delegate
  }
  return editor
}

@RequiresEdt
fun Editor.setMode(mode: NotebookEditorMode) {
  // Although LAB-50 is marked as closed, the checks still aren't added to classes written in Kotlin.
  ThreadingAssertions.assertEventDispatchThread()

  val modeChanged = mode != currentMode
  getEditor().putUserData(key, mode)

  if (modeChanged) {
    ApplicationManager.getApplication().messageBus.syncPublisher(NOTEBOOK_EDITOR_MODE).onModeChange(this, mode)
  }
}

internal val INVISIBLE_CARET = CaretVisualAttributes(Gray.TRANSPARENT,
                                                     CaretVisualAttributes.Weight.NORMAL)