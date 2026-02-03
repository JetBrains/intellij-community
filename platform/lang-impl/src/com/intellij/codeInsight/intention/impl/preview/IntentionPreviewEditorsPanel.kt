// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JPanel

internal class IntentionPreviewEditorsPanel(val editors: List<EditorEx>) : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    editors.forEachIndexed { index, editor ->
      add(editor.component)
      if (index < editors.size - 1) {
        add(createSeparatorLine(editor.colorsScheme))
      }
    }
  }

  private fun createSeparatorLine(colorsScheme: EditorColorsScheme): JPanel {
    var color = colorsScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
    color = color ?: JBColor.namedColor("Group.separatorColor", JBColor(Gray.xCD, Gray.x51))

    return JBUI.Panels.simplePanel().withBorder(JBUI.Borders.customLine(color, 1, 0, 0, 0))
  }

  companion object {
    fun createEditors(project: Project, result: IntentionPreviewDiffResult?): List<EditorEx> {
      if (result == null) return emptyList()

      val diffs = result.diffs
      if (diffs.isNotEmpty()) {
        val maxLine = diffs.maxOfOrNull { diff ->
          if (diff.startLine == -1) 0 else diff.startLine + diff.length } ?: 0
        return diffs.map { it.createEditor(project, maxLine) }
      }
      return emptyList()
    }

    private fun IntentionPreviewDiffResult.DiffInfo.createEditor(project: Project, maxLine: Int): EditorEx {
      val editor = createEditor(project, fileType, fileText, startLine, maxLine)
      for (fragment in fragments) {
        val attr = when (fragment.type) {
          IntentionPreviewDiffResult.HighlightingType.UPDATED -> DiffColors.DIFF_MODIFIED
          IntentionPreviewDiffResult.HighlightingType.ADDED -> DiffColors.DIFF_INSERTED
          IntentionPreviewDiffResult.HighlightingType.DELETED -> DiffColors.DIFF_DELETED
        }
        editor.markupModel.addRangeHighlighter(attr, fragment.start, fragment.end, HighlighterLayer.ERROR + 1,
                                               HighlighterTargetArea.EXACT_RANGE)
      }
      return editor
    }

    private fun createEditor(project: Project, fileType: FileType, text: String, lineShift: Int, maxLine: Int): EditorEx {
      val editorFactory = EditorFactory.getInstance()
      val document = editorFactory.createDocument(text)
      val editor = (editorFactory.createEditor(document, project, fileType, false) as EditorEx)
        .apply { setBorder(JBUI.Borders.empty(0, 10)) }

      editor.settings.apply {
        isLineNumbersShown = lineShift != -1
        isCaretRowShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        additionalColumnsCount = 4
        additionalLinesCount = 0
        isRightMarginShown = false
        isUseSoftWraps = false
        isAdditionalPageAtBottom = false
      }

      editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground
      editor.scrollPane.verticalScrollBar.isOpaque = false
      editor.colorsScheme.setColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR,
                                   editor.colorsScheme.getColor(EditorColors.LINE_NUMBERS_COLOR))

      editor.settings.isUseSoftWraps = true
      editor.scrollingModel.disableAnimation()

      editor.gutterComponentEx.apply {
        isPaintBackground = false
        if (lineShift != -1) {
          setLineNumberConverter(object : LineNumberConverter {
            override fun convert(editor: Editor, line: Int): Int = line + lineShift
            override fun getMaxLineNumber(editor: Editor): Int = maxLine
          })
        }
      }

      return editor
    }

  }
}