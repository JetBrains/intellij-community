// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors.SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.colors.EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.impl.TextOptions
import com.intellij.refactoring.rename.inplace.InplaceRefactoring.PRIMARY_VARIABLE_NAME

/**
 * @return a handle to update and/or remove highlights
 */
internal fun highlightTemplateVariables(
  project: Project,
  editor: Editor,
  template: Template,
  templateState: TemplateState,
  textOptions: TextOptions,
): InplaceTemplateHighlighting {
  val highlightManager: HighlightManager = HighlightManager.getInstance(project)
  val usageHighlighters = ArrayList<RangeHighlighter>()
  val commentStringHighlighters = ArrayList<RangeHighlighter>()
  val textHighlighters = ArrayList<RangeHighlighter>()
  for (i in 0 until templateState.segmentsCount) {
    val range = templateState.getSegmentRange(i)
    val variableName = template.getSegmentName(i)
    val key = if (variableName == PRIMARY_VARIABLE_NAME) WRITE_SEARCH_RESULT_ATTRIBUTES else SEARCH_RESULT_ATTRIBUTES
    val highlighters = when {
      variableName.startsWith(commentStringUsageVariablePrefix) -> commentStringHighlighters
      variableName.startsWith(plainTextUsageVariablePrefix) -> textHighlighters
      else -> usageHighlighters
    }
    highlightManager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, key, 0, highlighters)
  }
  for (highlighter: RangeHighlighter in usageHighlighters + commentStringHighlighters + textHighlighters) {
    highlighter.isGreedyToLeft = true
    highlighter.isGreedyToRight = true
  }
  return InplaceTemplateHighlighting(
    editor, highlightManager,
    usageHighlighters, commentStringHighlighters, textHighlighters
  )
}

private val NO_HIGHLIGHTING: TextAttributesKey = TextAttributesKey.createTextAttributesKey("no_highlighting")

// The logic of adding/removing highlights should be a part of TemplateState,
// because it already handles the ranges of template expressions
internal class InplaceTemplateHighlighting(
  private val editor: Editor,
  private val highlightManager: HighlightManager,
  private val usageHighlighters: List<RangeHighlighter>,
  private val commentStringHighlighters: List<RangeHighlighter>,
  private val textHighlighters: List<RangeHighlighter>,
) : Disposable {

  private var textOptions: TextOptions = TextOptions(true, true)

  internal fun updateHighlighters(newOptions: TextOptions) {
    if (textOptions == newOptions) {
      return
    }
    if (textOptions.commentStringOccurrences != newOptions.commentStringOccurrences) {
      val newKey = if (newOptions.commentStringOccurrences == true) SEARCH_RESULT_ATTRIBUTES else NO_HIGHLIGHTING
      updateHighlighters(commentStringHighlighters, newKey)
    }
    if (textOptions.textOccurrences != newOptions.textOccurrences) {
      val newKey = if (newOptions.textOccurrences == true) SEARCH_RESULT_ATTRIBUTES else NO_HIGHLIGHTING
      updateHighlighters(textHighlighters, newKey)
    }
    textOptions = newOptions
  }

  private fun updateHighlighters(highlighters: List<RangeHighlighter>, key: TextAttributesKey) {
    for (highlighter in highlighters) {
      highlighter.setTextAttributesKey(key)
    }
  }

  override fun dispose() {
    disposeHighlighters(usageHighlighters)
    disposeHighlighters(commentStringHighlighters)
    disposeHighlighters(textHighlighters)
  }

  private fun disposeHighlighters(highlighters: List<RangeHighlighter>) {
    for (highlighter: RangeHighlighter in highlighters) {
      highlightManager.removeSegmentHighlighter(editor, highlighter)
    }
  }
}
