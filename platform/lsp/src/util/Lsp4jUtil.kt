// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.util

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtilRt
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SnippetTextEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

fun getLsp4jPosition(document: Document, offset: Int): Position {
  if (document is DocumentWindow) {
    // It's very likely that the caller uses DocumentWindow not only when calling this function but also somewhere else.
    // The error helps to find the problematic place earlier.
    fileLogger().error("DocumentWindow is not expected here. Make sure to use DocumentWindow.delegate when working with LspClient.")
    return getLsp4jPosition(document.delegate, document.injectedToHost(offset))
  }

  val lineNumber = document.getLineNumber(offset)
  return Position(lineNumber, offset - document.getLineStartOffset(lineNumber))
}

fun getLsp4jRange(document: Document, offset: Int, length: Int): Range =
  Range(getLsp4jPosition(document, offset), getLsp4jPosition(document, offset + length))

/**
 * Returns `null` if [position] is outside the document text range.
 */
fun getOffsetInDocument(document: Document, position: Position): Int? {
  if (document is DocumentWindow) {
    // It's very likely that the caller uses DocumentWindow not only when calling this function but also somewhere else.
    // The error helps to find the problematic place earlier.
    fileLogger().error("DocumentWindow is not expected here. Make sure to use DocumentWindow.delegate when working with the LSP server.")
    return getOffsetInDocument(document.delegate, position)
  }

  val lineCount = document.lineCount
  val line = position.line
  var character = position.character
  if (line == lineCount && character == 0) return document.textLength
  if (line < 0 || line >= lineCount || character < 0) return null

  val lineStartOffset = document.getLineStartOffset(line)
  if (line + 1 < lineCount && character > 0) {
    // Make sure that the `character` value doesn't exceed the line length.
    // Some buggy servers may send range with `"character":80` for zero-length lines
    // (https://youtrack.jetbrains.com/issue/IDEA-332939#focus=Comments-27-8189497.0-0)
    // Secondly, we use this code with textDocument/foldingRange, that by spec may omit character.
    // We intermittently use `Int.MAX_VALUE` and expect to clamp it here to the EOL.
    // See [com.intellij.platform.lsp.impl.features.folding.LspFoldingRangeCache.foldingRangeToLsp4jRange]
    val nextLineStartOffset = document.getLineStartOffset(line + 1)
    character = min(character, nextLineStartOffset - 1 - lineStartOffset)
  }
  else if (line + 1 == lineCount && character > 0) {
    // workaround for servers that send { "line": <last_line>, "character": 2147483647 }
    character = min(character, document.textLength - lineStartOffset)
  }

  return (lineStartOffset + character).let { if (it <= document.textLength) it else null }
}

/**
 * Returns `null` if [range] is partially or fully outside the document text range.
 */
fun getRangeInDocument(document: Document, range: Range): TextRange? {
  val start = getOffsetInDocument(document, range.start) ?: return null
  val end = getOffsetInDocument(document, range.end) ?: return null
  if (!TextRange.isProperRange(start, end)) return null
  return TextRange(start, end)
}

/**
 * @return `true` if `textEdit` was applied successfully;
 * `false` if the `textEdit` can't be applied to the `document` because `textEdit.range` is outside the `document` text range
 */
fun applyTextEdit(document: Document, textEdit: TextEdit): Boolean =
  applyTextEdits(document, listOf(Either.forLeft(textEdit)))

@Deprecated("Use applyTextEdits(document, edits)", ReplaceWith("applyTextEdits(document, edits)"))
fun applySimpleTextEdits(document: Document, edits: List<Either<TextEdit, SnippetTextEdit>>): Boolean =
  applyTextEdits(document, edits)

/**
 * @return `true` if all `textEdits` were applied successfully;
 * or `false` as soon as some `textEdit` failed to get applied to the `document`
 * because `textEdit.range` was outside the `document` text range
 */
fun applyTextEdits(document: Document, textEdits: List<TextEdit>): Boolean =
  applyTextEdits(document, textEdits.map { Either.forLeft(it) })

/**
 * Applies the `edits` of one `TextDocumentEdit`.
 *
 * A [SnippetTextEdit] is applied as plain text: the snippet with its syntax removed.
 * The spec requires this for a document that is not open in the active editor.
 *
 * @param onApplied is called with each `edit` and the range of its new text, immediately after the edit is applied
 * @return `true` if all `edits` were applied successfully;
 * or `false` as soon as some `textEdit` failed to get applied to the `document`
 * because `textEdit.range` was outside the `document` text range
 */
@ApiStatus.Experimental
fun applyTextEdits(
  document: Document,
  edits: List<Either<TextEdit, SnippetTextEdit>>,
  onApplied: (Either<TextEdit, SnippetTextEdit>, TextRange) -> Unit = { _, _ -> },
): Boolean {
  // Spec:
  // > All text edits ranges refer to positions in the document they are computed on. Text edits ranges must never overlap.
  // > However, it is possible that multiple edits have the same start position: multiple inserts, ...
  // > If multiple inserts have the same position, the order in the array defines the order in which the inserted strings appear in the resulting text.
  //
  // The edits must be applied from bottom to top.
  // Edits that have the same position must be applied in the reversed order - this way the resulting text will get inserted strings in the original order.
  edits
    .sortedWith(compareBy({ it.range.start.line }, { it.range.start.character }))
    .reversed()
    .forEach { edit ->
      val newText = edit.left?.newText ?: Lsp4jService.getInstance().getSnippetText(edit.right!!.snippet.value)
      val newRange = applyEditAndGetRange(document, edit.range, newText) ?: return false
      onApplied(edit, newRange)
    }
  return true
}

private val Either<TextEdit, SnippetTextEdit>.range: Range
  get() = left?.range ?: right!!.range

/**
 * @return the range of the new text; `null` if the `range` is outside the `document` text range
 */
private fun applyEditAndGetRange(document: Document, range: Range, text: String): TextRange? {
  val startOffset = getOffsetInDocument(document, range.start)
  val endOffset = getOffsetInDocument(document, range.end)
  if (startOffset == null || endOffset == null) {
    fileLogger().warn("Ignoring TextEdit, its text range is outside the document text range.\n" +
                      "document.lineCount = ${document.lineCount}, document.textLength = ${document.textLength}, range: $range")
    return null
  }

  val newText = StringUtilRt.convertLineSeparators(text)
  document.replaceString(startOffset, endOffset, newText)
  return TextRange(startOffset, startOffset + newText.length)
}

/**
 * The message of the [Diagnostic], when the server sent it as a string.
 *
 * LSP 3.18 also permits a [MarkupContent] message, but only when the client sets the
 * `textDocument.diagnostic.markupMessageSupport` capability. The IDE does not set it, so a server must
 * send a string. This property gives an empty string for a markup message. It does not flatten the markup
 * to its raw value, because that loses the [MarkupContent.kind].
 */
val Diagnostic.messageIfStringOrEmpty: @NlsSafe String
  get() = message.map({ it }, { "" })
