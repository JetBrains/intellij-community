// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.util

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtilRt
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import kotlin.math.min

fun getLsp4jPosition(document: Document, offset: Int): Position {
  if (document is DocumentWindow) {
    // It's very likely that the caller uses DocumentWindow not only when calling this function but also somewhere else.
    // The error helps to find the problematic place earlier.
    fileLogger().error("DocumentWindow is not expected here. Make sure to use DocumentWindow.delegate when working with the LSP server.")
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
    // See [com.intellij.platform.lsp.impl.folding.LspFoldingRangeCache.foldingRangeToLsp4jRange]
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
 * @return `true` if all `textEdits` were applied successfully;
 * or `false` as soon as some `textEdit` failed to get applied to the `document`
 * because `textEdit.range` was outside the `document` text range
 */
fun applyTextEdits(document: Document, textEdits: List<TextEdit>): Boolean {
  // Spec:
  // > All text edits ranges refer to positions in the document they are computed on. Text edits ranges must never overlap.
  // > However, it is possible that multiple edits have the same start position: multiple inserts, ...
  // > If multiple inserts have the same position, the order in the array defines the order in which the inserted strings appear in the resulting text.
  //
  // The edits must be applied from bottom to top.
  // Edits that have the same position must be applied in the reversed order - this way the resulting text will get inserted strings in the original order.
  textEdits
    .sortedWith { edit1, edit2 ->
      (edit1.range.start.line - edit2.range.start.line).takeIf { it != 0 }
      ?: (edit1.range.start.character - edit2.range.start.character)
    }
    .reversed()
    .forEach { if (!applyTextEdit(document, it)) return@applyTextEdits false }

  return true
}

/**
 * @return `true` if `textEdit` was applied successfully;
 * `false` if the `textEdit` can't be applied to the `document` because `textEdit.range` is outside the `document` text range
 */
fun applyTextEdit(document: Document, textEdit: TextEdit): Boolean {
  val startOffset = getOffsetInDocument(document, textEdit.range.start)
  val endOffset = getOffsetInDocument(document, textEdit.range.end)
  if (startOffset == null || endOffset == null) {
    fileLogger().warn("Ignoring TextEdit, its text range is outside the document text range.\n" +
                      "document.lineCount = ${document.lineCount}, document.textLength = ${document.textLength}, range: ${textEdit.range}")
    return false
  }

  val newText = StringUtilRt.convertLineSeparators(textEdit.newText)
  document.replaceString(startOffset, endOffset, newText)
  return true
}
