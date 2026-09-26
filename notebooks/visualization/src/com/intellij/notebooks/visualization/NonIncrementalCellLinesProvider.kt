package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent

open class NonIncrementalCellLinesProvider protected constructor(private val intervalsGenerator: IntervalsGenerator) : NotebookCellLinesProvider, IntervalsGenerator {
  override fun create(document: Document): NotebookCellLines =
    NonIncrementalCellLines.get(document, intervalsGenerator)

  /* If NotebookCellLines doesn't exist, parse document once and don't create NotebookCellLines instance */
  override fun makeIntervals(document: Document, event: DocumentEvent?): List<NotebookCellLines.Interval> =
    NonIncrementalCellLines.getOrNull(document)?.intervals ?: intervalsGenerator.makeIntervals(document, event)
}