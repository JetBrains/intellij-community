// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.hover

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.ui.*
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import javax.swing.event.ChangeListener

class NotebookEditorCellHoverDetector(private val manager: NotebookCellInlayManager) : Disposable {
  private val editor = manager.editor

  private var mouseOverCell: EditorCell? = null

  private val scrollChange = ChangeListener {
    editor.contentComponent.mousePosition?.let {
      updateMouseOverCell(it)
    }
    editor.gutterComponentEx.mousePosition?.let {
      updateMouseOverCell(it)
    }
  }

  private val editorMouseListener = object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent) = updateMouseOverCell(e)
    override fun mouseExited(e: MouseEvent) = updateMouseOverCell(e)
  }

  private val editorMouseMotionEvent = object : MouseMotionListener {
    override fun mouseDragged(e: MouseEvent) = updateMouseOverCell(e)
    override fun mouseMoved(e: MouseEvent) = updateMouseOverCell(e)
  }

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPane()
    }

    manager.addCellViewEventsListener(object : EditorCellViewEventListener {
      override fun onEditorCellViewEvents(events: List<EditorCellViewEvent>) {
        events.asSequence().filterIsInstance<CellViewRemoved>().forEach {
          if (it == mouseOverCell) {
            mouseOverCell = null
          }
        }
      }
    }, this)

    EditorComponentWrapper.get(editor).apply {
      addEditorMouseEventListener(editorMouseListener)
      addEditorMouseMotionEvent(editorMouseMotionEvent)
    }
  }

  override fun dispose() {
    EditorComponentWrapper.get(editor).apply {
      removeEditorMouseEventListener(editorMouseListener)
      removeEditorMouseMotionEvent(editorMouseMotionEvent)
    }
  }

  private fun updateMouseOverCell(e: MouseEvent) {
    NotebookUiUtils.getEditorPoint(editor, e)?.let { (_, point) ->
      updateMouseOverCell(point)
    }
  }

  private fun setupScrollPane() {
    val scrollPane = editor.scrollPane
    editor.scrollPane.viewport.isOpaque = false
    scrollPane.viewport.addChangeListener(scrollChange)
    Disposer.register(this, Disposable { scrollPane.viewport.removeChangeListener(scrollChange) })
  }

  private fun updateMouseOverCell(point: Point) {
    val newCell = manager.getCellByPoint(point)
    val prevCell = mouseOverCell
    if (prevCell != newCell) {
      mouseOverCell = newCell
      newCell?.isHovered?.set(true)
      prevCell?.isHovered?.set(false)
      editor.notebookEditor.hoveredCell.set(newCell)
    }
  }

  companion object {
    fun install(manager: NotebookCellInlayManager) {
      NotebookEditorCellHoverDetector(manager).also {
        Disposer.register(manager, it)
      }
    }
  }
}