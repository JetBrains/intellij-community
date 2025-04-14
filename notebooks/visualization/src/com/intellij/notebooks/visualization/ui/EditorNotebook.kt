// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.observables.distinct
import com.intellij.notebooks.visualization.EditorNotebookExtension
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.ui.EditorCellEventListener.CellCreated
import com.intellij.notebooks.visualization.ui.EditorCellEventListener.CellRemoved
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.state.ObservableStateListener
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.register
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import kotlin.reflect.KClass

class EditorNotebook(private val editor: EditorImpl) : Disposable {

  private var _cells = mutableListOf<EditorCell>()

  val cells: List<EditorCell> get() = _cells.toList()

  private val _readOnly = AtomicBooleanProperty(false)
    .distinct()

  val readOnly: ObservableProperty<Boolean>
    get() = _readOnly

  val showCellToolbar: ObservableMutableProperty<Boolean> = AtomicBooleanProperty(true)
    .distinct()

  private val cellEventListeners = EventDispatcher.create(EditorCellEventListener::class.java)

  private val extensions = mutableMapOf<KClass<*>, EditorNotebookExtension>()

  init {
    EDITOR_NOTEBOOK_KEY.set(editor, this)
    editor.state.addPropertyChangeListener(object : ObservableStateListener {
      override fun propertyChanged(event: ObservableStateListener.PropertyChangeEvent) {
        if (event.propertyName == "isViewer") {
          _readOnly.set((event.newValue as Boolean))
        }
      }
    }, this)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : EditorNotebookExtension> getExtension(cls: KClass<T>): T? {
    return extensions[cls] as? T
  }

  private fun forEachExtension(action: (EditorNotebookExtension) -> Unit) {
    extensions.values.forEach { action(it) }
  }

  fun addCellEventsListener(disposable: Disposable, listener: EditorCellEventListener) {
    cellEventListeners.addListener(listener, disposable)
  }

  fun addCell(interval: NotebookIntervalPointer) {
    val editorCell = EditorCell(this, interval, editor).also {
      forEachExtension { extension ->
        extension.onCellCreated(it)
      }
      register(this, it)
    }
    _cells.add(interval.get()!!.ordinal, editorCell)
    cellEventListeners.multicaster.onEditorCellEvents(listOf(CellCreated(editorCell)))
  }

  override fun dispose() {
    extensions.values.forEach {
      if (it is Disposable) {
        Disposer.dispose(it)
      }
    }
    EDITOR_NOTEBOOK_KEY.set(editor, null)
    clear()
  }

  fun clear() {
    _cells.forEach { cell ->
      Disposer.dispose(cell)
    }
    _cells.clear()
  }

  fun removeCell(index: Int) {
    val cell = _cells[index]
    cell.onBeforeRemove()
    val removed = _cells.removeAt(index)
    Disposer.dispose(removed)
    cellEventListeners.multicaster.onEditorCellEvents(listOf(CellRemoved(removed, index)))
  }

  fun <T : EditorNotebookExtension> addExtension(type: KClass<T>, extension: T) {
    extensions[type] = extension
  }

  fun getNextVisibleCellBelow(ordinal: Int): EditorCell? {
    return getNextVisibleCellInDirection(ordinal, 1)
  }

  fun getNextVisibleCellAbove(ordinal: Int): EditorCell? {
    return getNextVisibleCellInDirection(ordinal, -1)
  }

  private fun getNextVisibleCellInDirection(ordinal: Int, direction: Int): EditorCell? {
    val range = if (direction > 0) {
      ordinal + direction until _cells.size
    }
    else {
      (0..ordinal + direction).reversed()
    }
    for (i in range) {
      val cell = _cells[i]
      if (cell.visible.get()) {
        return cell
      }
    }
    return null
  }
}

inline fun <reified T : EditorNotebookExtension> EditorNotebook.getExtension(): T? {
  return getExtension(T::class)
}

private val EDITOR_NOTEBOOK_KEY = Key<EditorNotebook>("EDITOR_NOTEBOOK_KEY")

val Editor.notebook: EditorNotebook?
  get() = EDITOR_NOTEBOOK_KEY.get(this)