package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent

interface IntervalsGenerator {
  fun makeIntervals(document: Document, event: DocumentEvent? = null): List<NotebookCellLines.Interval>
}