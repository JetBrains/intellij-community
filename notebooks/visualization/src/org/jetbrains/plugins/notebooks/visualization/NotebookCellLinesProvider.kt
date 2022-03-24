package org.jetbrains.plugins.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookCellLinesProvider"

interface NotebookCellLinesProvider : IntervalsGenerator {
  fun create(document: Document): NotebookCellLines

  companion object : LanguageExtension<NotebookCellLinesProvider>(ID)
}

interface IntervalsGenerator {
  fun makeIntervals(document: Document): List<NotebookCellLines.Interval>
}

open class NonIncrementalCellLinesProvider protected constructor(private val intervalsGenerator: IntervalsGenerator) : NotebookCellLinesProvider, IntervalsGenerator {
  override fun create(document: Document): NotebookCellLines =
    NonIncrementalCellLines.get(document, intervalsGenerator)

  /* If NotebookCellLines doesn't exist, parse document once and don't create NotebookCellLines instance */
  override fun makeIntervals(document: Document): List<NotebookCellLines.Interval> =
    NonIncrementalCellLines.getOrNull(document)?.intervals ?: intervalsGenerator.makeIntervals(document)
}

internal fun getLanguage(editor: Editor): Language? =
  editor
    .project
    ?.let(PsiDocumentManager::getInstance)
    ?.getPsiFile(editor.document)
    ?.language

val Editor.notebookCellLinesProvider: NotebookCellLinesProvider?
  get() = getLanguage(this)
    ?.let(NotebookCellLinesProvider::forLanguage)