package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.Document

fun Document.getContentText(cell: NotebookCellLines.Interval): CharSequence {
  return cell.getContentText(this)
}