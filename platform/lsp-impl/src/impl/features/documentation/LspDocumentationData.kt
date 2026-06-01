package com.intellij.platform.lsp.impl.features.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.XmlStringUtil
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
fun createLspDocumentationData(markupContent: MarkupContent): LspDocumentationData {
  if (markupContent.kind != MarkupKind.MARKDOWN) {
    if (markupContent.kind != MarkupKind.PLAINTEXT) {
      fileLogger().warn("Unexpected MarkupKind: ${markupContent.kind}, treating as plain text")
    }
    return LspDocumentationData(description = markupContent.value)
  }
  val contents = StringUtilRt.convertLineSeparators(markupContent.value)
  val endOfDefinition = contents.indexOf("```", 3)
  if (!contents.startsWith("```") || contents.indexOf("\n") <= 0 || endOfDefinition < 0) {
    return LspDocumentationData(description = contents, descriptionMarkup = LspDocumentationData.DescriptionMarkup.MARKDOWN)
  }
  val definitionLanguage = contents.takeWhile { !it.isWhitespace() }.substring(3).takeIf { it.isNotEmpty() }
  val definition = contents.substring(contents.indexOf("\n") + 1, endOfDefinition).trimIndent().trimEnd()

  @NlsSafe
  val description = contents.substring(endOfDefinition + 3)
  return LspDocumentationData(
    definitionCodeBlock = definition,
    definitionLanguage = definitionLanguage,
    description = description,
    descriptionMarkup = LspDocumentationData.DescriptionMarkup.MARKDOWN
  )
}

/**
 * Data class containing parsed information about the
 * documentation retrieved from the LSP server.
 *
 * The [definitionCodeBlock] contains raw code of the
 * returned definition. It is supposed to be shown
 * in the `definition` section of the QuickDoc. It should
 * be highlighted according to the rules for [definitionLanguage].
 *
 * The [description] contains description in [descriptionMarkup] format.
 */
@ApiStatus.Internal
data class LspDocumentationData(
  val definitionCodeBlock: @NlsSafe String? = null,
  val definitionLanguage: @NonNls String? = null,
  val description: @NlsSafe String? = null,
  val descriptionMarkup: DescriptionMarkup = DescriptionMarkup.PLAIN,
) {

  @RequiresReadLock
  fun toQuickDocHtml(project: Project): DocumentationResult.Documentation {
    @NlsSafe
    val htmlDefinition = definitionCodeBlock
      ?.let {
        QuickDocHighlightingHelper.getStyledSignatureFragment(
          project, QuickDocHighlightingHelper.guessLanguage(definitionLanguage), definitionCodeBlock)
      }
      ?.let { DocumentationMarkup.DEFINITION_START + it + DocumentationMarkup.DEFINITION_END }

    @NlsSafe
    val htmlDescription = description
      ?.let {
        when (descriptionMarkup) {
          DescriptionMarkup.MARKDOWN -> DocMarkdownToHtmlConverter.convert(
            project, description, QuickDocHighlightingHelper.guessLanguage(definitionLanguage))
            .trim().removePrefix("<hr />")
          DescriptionMarkup.PLAIN -> XmlStringUtil.escapeString(description)
        }
      }
      ?.takeIf { it.isNotBlank() }
      ?.let {
        DocumentationMarkup.CONTENT_START + it + DocumentationMarkup.CONTENT_END
      }
    return DocumentationResult.documentation((htmlDefinition ?: "") + (htmlDescription ?: ""))
  }

  enum class DescriptionMarkup {
    PLAIN,
    MARKDOWN,
  }
}
