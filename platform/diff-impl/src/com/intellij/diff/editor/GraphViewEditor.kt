package com.intellij.diff.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class GraphViewEditor(
  private val file: GraphViewVirtualFile
) : FileEditorBase() {
  init {

  }

  override fun getComponent(): JComponent = file.toolbarsAndTable
  override fun getPreferredFocusedComponent(): JComponent? = file.toolbarsAndTable

  override fun dispose() { }
  override fun isValid(): Boolean = !file.toolbarsAndTable.isValid
  override fun getFile(): VirtualFile = file
  override fun getName(): String = "GraphView"

  override fun selectNotify() {

  }
}