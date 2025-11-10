package com.intellij.database.run.ui

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.actions.DeleteRowsAction
import com.intellij.database.run.ui.grid.GridCopyProvider
import com.intellij.database.run.ui.grid.GridPasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.PlatformDataKeys.COPY_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.CUT_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.DELETE_ELEMENT_PROVIDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.PASTE_PROVIDER
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.editor.ex.EditorEx

class TableResultPanelUIDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val grid = snapshot[DatabaseDataKeys.DATA_GRID_KEY] ?: return
    sink[COPY_PROVIDER] = GridCopyProvider(grid)
    sink[PASTE_PROVIDER] = GridPasteProvider(grid, GridUtil::retrieveDataFromText)
    sink[DELETE_ELEMENT_PROVIDER] = DeleteRowsAction()
  }
}

class InnerEditorsTableResultPanelUIDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val editor = snapshot[EDITOR] as? EditorEx ?: return
    sink[COPY_PROVIDER] = editor.getCopyProvider()
    sink[PASTE_PROVIDER] = editor.getPasteProvider()
    sink[CUT_PROVIDER] = editor.getCutProvider()
    sink[DELETE_ELEMENT_PROVIDER] = editor.getDeleteProvider()
  }
}