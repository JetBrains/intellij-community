// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.util.ui.JBUI
import one.util.streamex.StreamEx
import java.awt.Color

internal class IntentionPreviewModel {
  companion object {

    private fun squash(lines: List<LineFragment>): List<LineFragment> = StreamEx.of(lines)
      .collapse({f1, f2 -> f2.startLine1 - f1.endLine1 == 1 && f2.startLine2 - f1.endLine2 == 1},
                { f1, f2 ->
                  LineFragmentImpl(f1.startLine1, f2.endLine1, f1.startLine2, f2.endLine2,
                                   f1.startOffset1, f2.endOffset1, f1.startOffset2, f2.endOffset2)
                }).toList()

    fun createEditors(project: Project, result: IntentionPreviewDiffResult?): List<EditorEx> {
      if (result == null) return emptyList()

      val psiFileCopy: PsiFile = result.psiFile

      val fileText = psiFileCopy.text
      val origFile = result.origFile
      val origText = origFile.text
      val diff = squash(result.lineFragments)
      var diffs = diff.mapNotNull { fragment ->
        val start = getOffset(fileText, fragment.startLine2)
        val end = getOffset(fileText, fragment.endLine2)
        if (start > end) return@mapNotNull null

        val origStart = getOffset(origText, fragment.startLine1)
        val origEnd = getOffset(origText, fragment.endLine1)
        if (origStart > origEnd) return@mapNotNull null

        val newText = fileText.substring(start, end).trimStart('\n').trimEnd('\n').trimIndent()
        val oldText = origText.substring(origStart, origEnd).trimStart('\n').trimEnd('\n').trimIndent()

        val deleted = newText.isBlank()
        if (deleted) {
          if (oldText.isBlank()) return@mapNotNull null
          return@mapNotNull DiffInfo(oldText, fragment.startLine1, fragment.endLine1 - fragment.startLine1, HighlightingType.DELETED)
        }

        var highlightRange: TextRange? = null
        var highlightingType = HighlightingType.UPDATED
        if (fragment.endLine2 - fragment.startLine2 == 1 && fragment.endLine1 - fragment.startLine1 == 1) {
          val prefix = StringUtil.commonPrefixLength(oldText, newText)
          val suffix = StringUtil.commonSuffixLength(oldText, newText).coerceAtMost(oldText.length - prefix)
          if (prefix > 0 || suffix > 0) {
            var endPos = newText.length - suffix
            if (endPos > prefix) {
              highlightRange = TextRange.create(prefix, endPos)
              if (oldText.length - suffix == prefix) {
                highlightingType = HighlightingType.ADDED
              }
            } else {
              endPos = oldText.length - suffix
              if (endPos > prefix) {
                val deletedLength = oldText.length - newText.length
                endPos = deletedLength.coerceAtLeast(prefix + deletedLength)
                highlightRange = TextRange.create(prefix, endPos)
                highlightingType = HighlightingType.DELETED
                return@mapNotNull DiffInfo(oldText, fragment.startLine1, fragment.endLine1 - fragment.startLine1, highlightingType, highlightRange)
              }
            }
          }
        }

        return@mapNotNull DiffInfo(newText, fragment.startLine1, fragment.endLine2 - fragment.startLine2, highlightingType, highlightRange)
      }
      if (diffs.any { info -> info.highlightingType != HighlightingType.DELETED || info.updatedRange != null }) {
        // Do not display deleted fragments if anything is added
        diffs = diffs.filter { info -> info.highlightingType != HighlightingType.DELETED || info.updatedRange != null }
      }
      if (diffs.isNotEmpty()) {
        val last = diffs.last()
        val maxLine = if (result.normalDiff) last.startLine + last.length else -1
        val fileName = result.fileName
        val editors = diffs.map { it.createEditor(project, origFile.fileType, maxLine) }
        val fileNameEditor = createFileNamePresentation(fileName, origFile, project)
        return listOfNotNull(fileNameEditor) + editors
      }
      return emptyList()
    }

    private fun createFileNamePresentation(fileName: String?, origFile: PsiFile, project: Project): EditorEx? {
      fileName ?: return null
      val commenter = LanguageCommenters.INSTANCE.forLanguage(origFile.language) ?: return null
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
      return DiffInfo(comment, 0, comment.length).createEditor(project, origFile.fileType, -1)
    }

    private enum class HighlightingType { ADDED, UPDATED, DELETED }

    private data class DiffInfo(val fileText: String,
                                val startLine: Int,
                                val length: Int,
                                val highlightingType: HighlightingType = HighlightingType.UPDATED,
                                val updatedRange: TextRange? = null) {
      fun createEditor(project: Project,
                       fileType: FileType,
                       maxLine: Int): EditorEx {
        val editor = createEditor(project, fileType, fileText, startLine, maxLine)
        if (updatedRange != null) {
          val attr = when (highlightingType) {
            HighlightingType.UPDATED -> DiffColors.DIFF_MODIFIED
            HighlightingType.ADDED -> DiffColors.DIFF_INSERTED
            HighlightingType.DELETED -> DiffColors.DIFF_DELETED
          }
          editor.markupModel.addRangeHighlighter(attr, updatedRange.startOffset, updatedRange.endOffset, HighlighterLayer.ERROR + 1,
                                                 HighlighterTargetArea.EXACT_RANGE)
        } else if (highlightingType == HighlightingType.DELETED) {
          val document = editor.document
          val lineCount = document.lineCount
          for (line in 0 until lineCount) {
            var start = document.getLineStartOffset(line)
            var end = document.getLineEndOffset(line) - 1
            while (start <= end && Character.isWhitespace(fileText[start])) start++
            while (start <= end && Character.isWhitespace(fileText[end])) end--
            if (start <= end) {
              editor.markupModel.addRangeHighlighter(DiffColors.DIFF_DELETED, start, end + 1, HighlighterLayer.ERROR + 1,
                                                     HighlighterTargetArea.EXACT_RANGE)
            }
          }
        }
        return editor
      }
    }

    private fun getOffset(fileText: String, lineNumber: Int): Int {
      return StringUtil.lineColToOffset(fileText, lineNumber, 0).let { pos -> if (pos == -1) fileText.length else pos }
    }

    private fun createEditor(project: Project, fileType: FileType, text: String, lineShift: Int, maxLine: Int): EditorEx {
      val editorFactory = EditorFactory.getInstance()
      val document = editorFactory.createDocument(text)
      val editor = (editorFactory.createEditor(document, project, fileType, false) as EditorEx)
        .apply { setBorder(JBUI.Borders.empty(2, 0, 2, 0)) }

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
      editor.colorsScheme.setColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR, editor.colorsScheme.getColor(EditorColors.LINE_NUMBERS_COLOR))

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