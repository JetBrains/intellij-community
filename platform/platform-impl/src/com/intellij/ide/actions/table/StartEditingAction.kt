// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.table

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.SwingActionDelegate
import com.intellij.util.ui.table.EditableTable
import javax.swing.JTable

internal class StartEditingAction : DumbAwareAction() {
  private val AnActionEvent.contextTable
    get() = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JTable

  private fun getEditableTable(table: JTable) = table.model as? EditableTable
                                                ?: table as? EditableTable
                                                ?: table.getClientProperty(EditableTable.KEY) as? EditableTable

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val table = event.contextTable ?: return
    event.presentation.isVisible = true
    // enable editing if the selected cell is editable
    val row = table.selectionModel.leadSelectionIndex
    val column = table.columnModel.selectionModel.leadSelectionIndex
    if (row < 0 || row >= table.rowCount || column < 0 || column >= table.columnCount) return
    event.presentation.isEnabled = table.run { isCellEditable(row, column) && getCellEditor(row, column)?.isCellEditable(null) == true }
    // update action presentation according to the selected cell
    getEditableTable(table)?.updateAction(event.presentation, row, column)
  }

  override fun actionPerformed(event: AnActionEvent) {
    // javax.swing.plaf.basic.BasicTableUI.Actions.START_EDITING
    SwingActionDelegate.performAction("startEditing", event.contextTable)
  }

  init {
    isEnabledInModalContext = true
  }
}
