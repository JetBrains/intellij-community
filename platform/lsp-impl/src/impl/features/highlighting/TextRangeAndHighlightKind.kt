package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.util.getRangeInDocument
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind

internal data class TextRangeAndHighlightKind(val textRange: TextRange, val kind: DocumentHighlightKind?) {
  companion object {
    fun fromDocumentHighlights(highlights: List<DocumentHighlight>, document: Document): List<TextRangeAndHighlightKind> {
      return highlights.mapNotNull { highlight ->
        val textRange = getRangeInDocument(document, highlight.range) ?: return@mapNotNull null
        TextRangeAndHighlightKind(textRange, highlight.kind)
      }
    }
  }
}
