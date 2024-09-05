package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.Document

fun Document.getContentText(cell: NotebookCellLines.Interval): CharSequence {
  val first = cell.firstContentLine
  val last = cell.lastContentLine
  return if (first <= last) charsSequence.subSequence(getLineStartOffset(first), getLineEndOffset(last)) else ""
}