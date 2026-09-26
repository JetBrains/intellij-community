// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx

internal class ErrorWarningAudioCueDetector : EditorAudioCueDetector {
  private val errorLine = EditorAudioCue(IdeAudioCues.ERROR_LINE)
  private val errorCaret = EditorAudioCue(IdeAudioCues.ERROR_CARET, lineCounterpart = IdeAudioCues.ERROR_LINE)
  private val warningLine = EditorAudioCue(IdeAudioCues.WARNING_LINE)
  private val warningCaret = EditorAudioCue(IdeAudioCues.WARNING_CARET, lineCounterpart = IdeAudioCues.WARNING_LINE)

  override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    val editorEx = editor as? EditorEx ?: return emptySet()
    val project = editor.project?.takeUnless { it.isDisposed } ?: return emptySet()
    val document = editorEx.document
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project)
    val result = mutableSetOf<EditorAudioCue>()

    editorEx.filteredDocumentMarkupModel.processRangeHighlightersOverlappingWith(lineStart, lineEnd) { marker ->
      val info = HighlightInfo.fromRangeHighlighter(marker)
      if (info != null && !info.isFileLevelAnnotation && severityRegistrar.compare(info.severity, HighlightSeverity.WARNING) >= 0) {
        val isError = severityRegistrar.compare(info.severity, HighlightSeverity.ERROR) >= 0
        result += if (isError) errorLine else warningLine
        if (marker.containsInclusive(caretOffset)) {
          result += if (isError) errorCaret else warningCaret
        }
      }
      true
    }
    return result
  }
}
