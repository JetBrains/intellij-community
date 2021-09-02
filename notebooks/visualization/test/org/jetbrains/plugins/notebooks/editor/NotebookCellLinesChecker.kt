package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import org.assertj.core.api.Assertions.assertThat

class NotebookCellLinesChecker(private val lexer: NotebookCellLinesLexer) {
  fun check(document: Document, cellLines: NotebookCellLines) {
    val singleUseImpl = NotebookCellLinesImpl.getForSingleUsage(document, lexer)
    val expectedIntervals = singleUseImpl.intervalsIterator(0).asSequence().toList()
    val cachedIntervals = cellLines.intervalsIterator(0).asSequence().toList()

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
