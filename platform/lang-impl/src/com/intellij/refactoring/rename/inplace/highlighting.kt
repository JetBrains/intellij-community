// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors.SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.colors.EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.PRIMARY_VARIABLE_NAME

/**
 * @return a handle to remove highlights
 */
internal fun highlightTemplateVariables(
  project: Project,
  editor: Editor,
  template: Template,
  templateState: TemplateState,
): Disposable {
  val highlighters = ArrayList<RangeHighlighter>()
  val highlightManager: HighlightManager = HighlightManager.getInstance(project)
  for (i in 0 until templateState.segmentsCount) {
    val range = templateState.getSegmentRange(i)
    val variableName = template.getSegmentName(i)
    val key = if (variableName == PRIMARY_VARIABLE_NAME) WRITE_SEARCH_RESULT_ATTRIBUTES else SEARCH_RESULT_ATTRIBUTES
    highlightManager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, key, 0, highlighters)
  }
  for (highlighter: RangeHighlighter in highlighters) {
    highlighter.isGreedyToLeft = true
    highlighter.isGreedyToRight = true
  }
  return Disposable {
    for (highlighter: RangeHighlighter in highlighters) {
      highlightManager.removeSegmentHighlighter(editor, highlighter)
    }
  }
}
