// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.selection

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.Disposable

internal class EditorCellSelectionModel() : Disposable {
  private val _selection = mutableSetOf<EditorCell>()

  val selection: List<EditorCell>
    get() = _selection.sortedBy { it.interval.ordinal }

  override fun dispose() {
    _selection.clear()
  }

  fun removeCell(selectedCell: EditorCell) {
    _selection.remove(selectedCell)
    selectedCell.isSelected.set(false)
  }

  fun replaceSelection(cells: Collection<EditorCell>) {
    val selectionSet = cells.toSet()
    val toRemove = _selection - selectionSet
    val toAdd = selectionSet - _selection
    toRemove.forEach {
      it.isSelected.set(false)
    }
    toAdd.forEach {
      it.isSelected.set(true)
    }
    _selection.removeAll(toRemove)
    _selection.addAll(toAdd)
  }
}