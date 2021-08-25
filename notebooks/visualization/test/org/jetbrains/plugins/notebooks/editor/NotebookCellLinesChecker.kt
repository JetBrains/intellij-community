package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import org.assertj.core.api.Assertions.assertThat

class NotebookCellLinesChecker(private val lexer: NotebookCellLinesLexer) {
  private fun extractState(notebookCellLines: NotebookCellLines): Pair<List<NotebookCellLines.Marker>, List<NotebookCellLines.Interval>> {
    val markers = notebookCellLines.markersIterator(0).asSequence().toList()
    val intervals = notebookCellLines.intervalsIterator(0).asSequence().toList()
    return Pair(markers, intervals)
  }

  fun check(document: Document, cellLines: NotebookCellLines) {
    val singleUseImpl = NotebookCellLinesImpl.getForSingleUsage(document, lexer)
    val (expectedMarkers, expectedIntervals) = extractState(singleUseImpl)
    val (cachedMarkers, cachedIntervals) = extractState(cellLines)

    assertThat(cachedMarkers).containsExactly(*expectedMarkers.toTypedArray())
    assertThat(cachedIntervals).containsExactly(*expectedIntervals.toTypedArray())
  }

  companion object {
    fun get(editor: Editor): NotebookCellLinesChecker {
      val psiDocumentManager = PsiDocumentManager.getInstance(editor.project!!)
      val document = editor.document
      val psiFile = psiDocumentManager.getPsiFile(document) ?: error("document ${document} doesn't have PSI file")
      val lexer = NotebookCellLinesProvider.forLanguage(psiFile.language) as NotebookCellLinesLexer
      return NotebookCellLinesChecker(lexer)
    }
  }
}
