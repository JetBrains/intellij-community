// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingModelEx

internal class FoldedCodeAudioCueDetector : EditorAudioCueDetector {
  private val foldedLine = EditorAudioCue(IdeAudioCues.FOLDED_LINE)
  private val foldedCaret = EditorAudioCue(IdeAudioCues.FOLDED_CARET, lineCounterpart = IdeAudioCues.FOLDED_LINE)

  override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    val foldingModel = editor.foldingModel as? FoldingModelEx ?: return emptySet()
    val document = editor.document
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val result = mutableSetOf<EditorAudioCue>()
    if (foldingModel.getRegionsOverlappingWith(lineStart, lineEnd).any { isFoldedCode(it) }) {
      result += foldedLine
    }
    if (foldingModel.getRegionsOverlappingWith(caretOffset, caretOffset).any { isFoldedCode(it) }) {
      result += foldedCaret
    }
    return result
  }

  private fun isFoldedCode(region: FoldRegion): Boolean = region.isValid && !region.isExpanded && region !is CustomFoldRegion
}
