// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.Color

internal class IntentionPreviewModel {
  companion object {

    fun createEditors(project: Project, result: IntentionPreviewDiffResult?): List<EditorEx> {
      if (result == null) return emptyList()

      val diffs = result.createDiffs()
      if (diffs.isNotEmpty()) {
        val last = diffs.last()
        val maxLine = if (result.normalDiff) last.startLine + last.length else -1
        val fileName = result.fileName
        val editors = diffs.map { it.createEditor(project, result.fileType, maxLine) }
        val fileNameEditor = createFileNamePresentation(fileName, result, project)
        return listOfNotNull(fileNameEditor) + editors
      }
      return emptyList()
    }

    private fun createFileNamePresentation(fileName: String?, result: IntentionPreviewDiffResult, project: Project): EditorEx? {
      fileName ?: return null
      val language = (result.fileType as? LanguageFileType)?.language ?: return null
      val commenter = LanguageCommenters.INSTANCE.forLanguage(language) ?: return null
      var comment: String? = null
      val linePrefix = commenter.lineCommentPrefix
      if (linePrefix != null) {
        comment = "$linePrefix $fileName"
      }
      else {
        val prefix = commenter.blockCommentPrefix
        val suffix = commenter.blockCommentSuffix
        if (prefix != null && suffix != null) {
          comment = "$prefix $fileName $suffix"
        }
      }
      comment ?: return null
      return IntentionPreviewDiffResult.DiffInfo(comment, 0, comment.length, listOf())
        .createEditor(project, result.fileType, -1)
    }

    private fun IntentionPreviewDiffResult.DiffInfo.createEditor(project: Project, fileType: FileType, maxLine: Int): EditorEx {
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
        .apply { setBorder(JBUI.Borders.empty(2, 0)) }

      editor.settings.apply {
        isLineNumbersShown = maxLine != -1
        isCaretRowShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        additionalColumnsCount = 4
        additionalLinesCount = 0
        isRightMarginShown = false
        isUseSoftWraps = false
        isAdditionalPageAtBottom = false
      }

      editor.backgroundColor = getEditorBackground()
      editor.colorsScheme.setColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR,
                                   editor.colorsScheme.getColor(EditorColors.LINE_NUMBERS_COLOR))

      editor.settings.isUseSoftWraps = true
      editor.scrollingModel.disableAnimation()

      editor.gutterComponentEx.apply {
        isPaintBackground = false
        if (maxLine != -1) {
          setLineNumberConverter(object : LineNumberConverter {
            override fun convert(editor: Editor, line: Int): Int = line + lineShift
            override fun getMaxLineNumber(editor: Editor): Int = maxLine
          })
        }
      }

      return editor
    }

    private fun getEditorBackground(): Color {
      return EditorColorsManager.getInstance().globalScheme.defaultBackground
    }
  }
}