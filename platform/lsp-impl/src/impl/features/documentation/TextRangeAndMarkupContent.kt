package com.intellij.platform.lsp.impl.features.documentation

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.util.getRangeInDocument
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TextRangeAndMarkupContent(val textRange: TextRange, val markupContent: MarkupContent) {
  internal companion object {
    internal fun fromHover(hover: Hover, document: Document?, offset: Int): TextRangeAndMarkupContent? {
      val range = hover.range
      val textRange = if (document != null && range != null) {
        getRangeInDocument(document, range) ?: TextRange(offset, offset)
      }
      else TextRange(offset, offset)

      val markupContent = hover.contents.right
      if (markupContent != null) {
        if (markupContent.value.isNullOrBlank()) return null
        return TextRangeAndMarkupContent(textRange, markupContent)
      }

      val markup = hover.contents.left!!.joinToString("\n\n") { markupOrMarkedString ->
        if (markupOrMarkedString.isLeft) {
          markupOrMarkedString.left
        }
        else {
          val markedString = markupOrMarkedString.right!!
          val codeSnippet = markedString.value
          val language = markedString.language
          "```${language ?: ""}\n$codeSnippet\n```"
        }
      }

      if (markup.isBlank()) return null
      return TextRangeAndMarkupContent(textRange, MarkupContent(MarkupKind.MARKDOWN, markup))
    }
  }
}