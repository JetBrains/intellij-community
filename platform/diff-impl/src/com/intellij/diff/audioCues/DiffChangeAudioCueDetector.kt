// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.audioCues

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.ide.audioCues.EditorAudioCue
import com.intellij.ide.audioCues.EditorAudioCueDetector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import org.jetbrains.annotations.ApiStatus

/**
 * Every diff and merge viewer installs its line highlighters via [DiffDrawUtil] with
 * [DiffDrawUtil.DiffTextAttributes] carrying the change type. Resolved merge changes and skipped diff changes
 * get no such attributes, so they stay silent.
 */
@ApiStatus.Internal
class DiffChangeAudioCueDetector : EditorAudioCueDetector {
  private val lineInserted = EditorAudioCue(DiffAudioCues.LINE_INSERTED)
  private val lineDeleted = EditorAudioCue(DiffAudioCues.LINE_DELETED)
  private val lineModified = EditorAudioCue(DiffAudioCues.LINE_MODIFIED)
  private val lineConflict = EditorAudioCue(DiffAudioCues.LINE_CONFLICT)

  override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    if (editor.editorKind != EditorKind.DIFF) return emptySet()
    val editorEx = editor as? EditorEx ?: return emptySet()
    val document = editorEx.document
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)

    val lineHighlighters = mutableListOf<RangeHighlighterEx>()
    editorEx.markupModel.processRangeHighlightersOverlappingWith(lineStart, lineEnd) { highlighter ->
      if (highlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE) {
        lineHighlighters += highlighter
      }
      true
    }

    val result = mutableSetOf<EditorAudioCue>()
    for (highlighter in lineHighlighters) {
      val attributes = highlighter.getTextAttributes(editorEx.colorsScheme) as? DiffDrawUtil.DiffTextAttributes
      when (attributes?.type) {
        TextDiffType.INSERTED -> result += lineInserted
        TextDiffType.DELETED -> result += lineDeleted
        TextDiffType.MODIFIED -> result += lineModified
        TextDiffType.CONFLICT -> result += lineConflict
        else -> {}
      }
    }
    return result
  }
}
