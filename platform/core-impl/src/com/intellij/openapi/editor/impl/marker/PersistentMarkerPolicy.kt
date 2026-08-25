// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.lineDiff
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.ApiStatus

/**
 * Range-marker policy that attempts to retain a marker through large replacements by translating its line and column
 * coordinates through a line diff. If diff translation is unavailable, it falls back to [DefaultMarkerPolicy].
 */
@ApiStatus.Internal
object PersistentMarkerPolicy : MarkerPolicy {
  fun requiresFullTraversal(
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): Boolean = patch.mayUseDiffTranslation(beforeText, afterText)

  override fun transform(
    entry: MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    if (!entry.shouldTranslateViaDiff(patch, beforeText, afterText)) {
      return DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    }
    val translated = translate(entry, patch, beforeText, afterText)
    return if (translated != null) {
      MarkerTransformResult.Valid(translated)
    }
    else {
      DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    }
  }

  private fun translate(
    entry: MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerEntry? {
    val startLine = beforeText.lineNumber(entry.startOffset)
    val endLine = beforeText.lineNumber(entry.endOffset)
    val startColumn = entry.startOffset - beforeText.lineStartOffset(startLine)
    val endColumn = entry.endOffset - beforeText.lineStartOffset(endLine)

    val lineDiff = patch.lineDiff(beforeText)
    val changeStartLine = afterText.lineNumber(lineDiff.changeStartOffset)
    val afterChars = afterText.chars()
    val translatedStartLine: Int
    val translatedEndLine: Int
    try {
      translatedStartLine = lineDiff.translateLineStrict(startLine, changeStartLine, afterChars)
      translatedEndLine = lineDiff.translateLineStrict(endLine, changeStartLine, afterChars)
    }
    catch (_: FilesTooBigForDiffException) {
      return null
    }
    if (translatedStartLine !in 0..<afterText.lineCount()) return null
    if (translatedEndLine !in 0..<afterText.lineCount()) return null

    val startOffset = afterText.lineStartOffset(translatedStartLine) + startColumn
    if (startOffset >= afterText.length()) return null
    val endOffset = afterText.lineStartOffset(translatedEndLine) + endColumn
    if (endOffset > afterText.length() || endOffset < startOffset) return null
    if (translatedEndLine < translatedStartLine) return null
    if (translatedStartLine == translatedEndLine && endColumn < startColumn) return null

    return entry.copy(startOffset = startOffset, endOffset = endOffset)
  }

  private fun MarkerEntry.shouldTranslateViaDiff(
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): Boolean {
    if (patch.isWholeTextReplacement(beforeText)) return true
    if (patch.startOffset() >= endOffset || patch.endOffset() <= startOffset) return false
    return patch.mayUseDiffTranslation(beforeText, afterText)
  }

  private fun DocumentTextPatch.mayUseDiffTranslation(beforeText: DocumentText, afterText: DocumentText): Boolean {
    if (isWholeTextReplacement(beforeText)) return true
    val oldLength = endOffset() - startOffset()
    return maxOf(newFragment().length, oldLength) * 5 >= afterText.length() * 4
  }

  private fun DocumentTextPatch.isWholeTextReplacement(beforeText: DocumentText): Boolean {
    return beforeText.length() != 0 && originStartOffset() == 0 && originEndOffset() == beforeText.length()
  }
}
