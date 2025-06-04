// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.selection

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.getSelectionLines
import com.intellij.notebooks.visualization.hasIntersectionWith
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

class NotebookEditorCellSelectionDetector(private val manager: NotebookCellInlayManager) : Disposable.Default {
  private val editorImpl = manager.editor

  private var selectionUpdateScheduled = AtomicBoolean(false)
  
  private val selectionModel = EditorCellSelectionModel().also {
    Disposer.register(this, it)
  }

  init {
    manager.addCellEventsListener(object : EditorCellEventListener {
      override fun onEditorCellEvents(events: List<EditorCellEventListener.EditorCellEvent>) {
        val removedCells = events.filterIsInstance<EditorCellEventListener.CellRemoved>()
        for (event in removedCells) {
          selectionModel.removeCell(event.cell)
        }
      }
    }, this)


    editorImpl.caretModel.addCaretListener(object : CaretListener {
      override fun caretAdded(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretPositionChanged(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretRemoved(event: CaretEvent) = scheduleSelectionUpdate()
    })

    updateSelectionByCarets()
  }

  private fun scheduleSelectionUpdate() {
    if (selectionUpdateScheduled.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater {
        try {
          if (!editorImpl.isDisposed) {
            updateSelectionByCarets()
          }
        }
        finally {
          selectionUpdateScheduled.set(false)
        }
      }
    }
  }

  private fun updateSelectionByCarets() {
    selectionModel.replaceSelection(
      editorImpl.caretModel.allCarets.flatMap { getCellsByCaretSelection(it) }
    )
  }

  private fun getCellsByCaretSelection(caret: Caret): List<EditorCell> {
    val lines = editorImpl.document.getSelectionLines(caret)
    return manager.cells.filter { it.interval.lines.hasIntersectionWith(lines) }
  }


  companion object {
    fun install(manager: NotebookCellInlayManager) {
      NotebookEditorCellSelectionDetector(manager).also {
        Disposer.register(manager, it)
      }
    }
  }
}