// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.lineDiff
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.ApiStatus

/**
 * Persistent line translation for an exact-range or line-range highlighter.
 */
@ApiStatus.Internal
enum class PersistentHighlighterPolicy(private val wholeLineRange: Boolean) : MarkerPolicy {
  EXACT_RANGE(false),
  LINES_IN_RANGE(true);

  override val isPersistent: Boolean
    get() = true

  override fun transform(
      entry: MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): MarkerTransformResult {
    if (entry.shouldTranslateViaDiff(patch, beforeText, afterText)) {
      try {
        val lineDiff = patch.lineDiff(beforeText)
        val startLine = beforeText.lineNumber(entry.nodeStart)
        val changeStartLine = afterText.lineNumber(lineDiff.changeStartOffset)
        val translatedLine = lineDiff.translateLineStrict(startLine, changeStartLine, afterText.chars())
        if (translatedLine !in 0..<afterText.lineCount()) {
          return MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
        }
        return normalizeLine(afterText, translatedLine)
      }
      catch (_: FilesTooBigForDiffException) {
      }
    }

    val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    if (!transformed.isValid) return transformed

    val startLine = afterText.lineNumber(transformed.startOffset)
    val endLine = afterText.lineNumber(transformed.endOffset)
    return if (wholeLineRange) {
      normalizeLine(afterText, startLine)
    }
    else if (endLine != startLine) {
      MarkerTransformResult(transformed.startOffset, afterText.lineEndOffset(startLine))
    }
    else {
      transformed
    }
  }

  private fun normalizeLine(text: DocumentText, line: Int): MarkerTransformResult {
    val lineStart = text.lineStartOffset(line)
    val lineEnd = text.lineEndOffset(line)
    val startOffset = if (wholeLineRange) firstNonSpaceOffset(text.luckyChars(), lineStart, lineEnd) else lineStart
    return MarkerTransformResult(startOffset, lineEnd)
  }

  private fun MarkerEntry.shouldTranslateViaDiff(
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): Boolean {
    if (beforeText.length() != 0 && patch.originStartOffset() == 0 && patch.originEndOffset() == beforeText.length()) return true
    if (patch.startOffset() >= nodeEnd || patch.endOffset() <= nodeStart) return false
    return PersistentMarkerPolicy.requiresFullTraversal(patch, beforeText, afterText)
  }

  private fun firstNonSpaceOffset(text: CharSequence, lineStart: Int, lineEnd: Int): Int {
    for (offset in lineStart..<lineEnd) {
      val character = text[offset]
      if (character != ' ' && character != '\t') return offset
    }
    return lineStart
  }
}
