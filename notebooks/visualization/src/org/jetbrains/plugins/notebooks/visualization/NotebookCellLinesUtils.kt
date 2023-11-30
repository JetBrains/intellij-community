package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

fun Document.getContentText(cell: NotebookCellLines.Interval): String {
  val first = cell.firstContentLine
  val last = cell.lastContentLine
  if (first <= last) {
    return getText(TextRange(getLineStartOffset(first), getLineEndOffset(last)))
  }
  return ""
}