// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.CalledInAny
import java.awt.Color

/**
 * The Jupyter Notebook has a modal user interface.
 * This means that the keyboard does different things depending on which mode the Notebook is in.
 * There are two modes: edit mode and command mode.
 *
 * @see <a href="https://jupyter-notebook.readthedocs.io/en/stable/examples/Notebook/Notebook%20Basics.html#Modal-editor">
 *   Notebook Modal Editor
 *   </a>
 */
enum class NotebookEditorMode {
  EDIT,
  COMMAND
}

val NOTEBOOK_EDITOR_MODE: Topic<NotebookEditorModeListener> = Topic.create("Notebook Editor Mode",
                                                                           NotebookEditorModeListener::class.java)

@FunctionalInterface
interface NotebookEditorModeListener {

  fun onModeChange(mode: NotebookEditorMode)
}

abstract class NotebookEditorModeListenerAdapter : TextEditor, NotebookEditorModeListener, CaretListener {
  private var currentEditorMode: NotebookEditorMode? = null

  private fun getCaretAttributes(mode: NotebookEditorMode): CaretVisualAttributes {
    return when (mode) {
      NotebookEditorMode.EDIT -> CaretVisualAttributes.DEFAULT
      NotebookEditorMode.COMMAND -> INVISIBLE_CARET
    }
  }

  private fun isCaretRowShown(mode: NotebookEditorMode): Boolean =
    when (mode) {
      NotebookEditorMode.EDIT -> true
      NotebookEditorMode.COMMAND -> false
    }

  private fun handleCarets(mode: NotebookEditorMode) {
    when (mode) {
      NotebookEditorMode.EDIT -> {
        // selection of multiple cells leads to multiple invisible carets, remove them
        editor.caretModel.removeSecondaryCarets()
      }
      NotebookEditorMode.COMMAND -> {
        // text selection shouldn't be visible in command mode
        for (caret in editor.caretModel.allCarets) {
          caret.removeSelection()
        }
      }
    }
  }

  override fun onModeChange(mode: NotebookEditorMode) {
    val modeWasChanged = currentEditorMode != mode

    currentEditorMode = mode

    editor.apply {
      (markupModel as MarkupModelEx).apply {
        allHighlighters.filterIsInstance<RangeHighlighterEx>().forEach {
          fireAttributesChanged(it, true, false)
        }
      }

      if (modeWasChanged) {
        handleCarets(mode)
        editor.settings.isCaretRowShown = isCaretRowShown(mode)
      }

      caretModel.allCarets.forEach { caret ->
        caret.visualAttributes = getCaretAttributes(mode)
      }

      editor.contentComponent.putClientProperty(ActionUtil.ALLOW_PlAIN_LETTER_SHORTCUTS, when (mode) {
        NotebookEditorMode.EDIT -> false
        NotebookEditorMode.COMMAND -> true
      })
    }
  }

  override fun caretAdded(event: CaretEvent) {
    val mode = currentEditorMode ?: return
    event.caret?.visualAttributes = getCaretAttributes(mode)
    (editor as EditorEx).gutterComponentEx.repaint()
  }

  override fun caretRemoved(event: CaretEvent) {
    (editor as EditorEx).gutterComponentEx.repaint()
  }
}


@CalledInAny
fun currentMode(): NotebookEditorMode = currentMode_

@RequiresEdt
fun setMode(mode: NotebookEditorMode) {
  // Although LAB-50 is marked as closed, the checks still aren't added to classes written in Kotlin.
  ApplicationManager.getApplication().assertIsDispatchThread()

  currentMode_ = mode

  // may be call should be skipped if mode == currentMode_
  ApplicationManager.getApplication().messageBus.syncPublisher(NOTEBOOK_EDITOR_MODE).onModeChange(mode)
}

@Volatile
private var currentMode_: NotebookEditorMode = NotebookEditorMode.EDIT



private val INVISIBLE_CARET = CaretVisualAttributes(
  Color(0, 0, 0, 0),
  CaretVisualAttributes.Weight.NORMAL)

