// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.TestOnly

data class IntentionPreviewDiffResult(val diffs: List<DiffInfo>, @TestOnly val newText: String) : IntentionPreviewInfo {
  enum class HighlightingType { ADDED, UPDATED, DELETED }

  data class Fragment(val type: HighlightingType, val start: Int, val end: Int)

  data class DiffInfo(
    val fileType: FileType,
    val fileText: String,
    val startLine: Int,
    val length: Int,
    val fragments: List<Fragment>)

  companion object {
    private fun getOffset(fileText: String, lineNumber: Int): Int {
      return StringUtil.lineColToOffset(fileText, lineNumber, 0).let { pos -> if (pos == -1) fileText.length else pos }
    }

    private fun squash(lines: List<LineFragment>): List<LineFragment> = StreamEx.of(lines)
      .collapse({ f1, f2 -> f2.startLine1 - f1.endLine1 == 1 && f2.startLine2 - f1.endLine2 == 1 },
                { f1, f2 ->
                  LineFragmentImpl(f1.startLine1, f2.endLine1, f1.startLine2, f2.endLine2,
                                   f1.startOffset1, f2.endOffset1, f1.startOffset2, f2.endOffset2)
                }).toList()

    private fun createFileNamePresentation(fileType: FileType, fileName: String?): DiffInfo? {
      fileName ?: return null
      val language = (fileType as? LanguageFileType)?.language ?: return null
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
      return DiffInfo(fileType, comment, -1, comment.length, listOf())
    }

    @JvmStatic
    fun create(fileType: FileType,
               updatedText: String,
               origText: String,
               policy: ComparisonPolicy,
               normalDiff: Boolean = true,
               fileName: String? = null): IntentionPreviewDiffResult {
      var lineFragments = ComparisonManager.getInstance().compareLines(
        origText, updatedText, policy, DumbProgressIndicator.INSTANCE)
      if (lineFragments.isEmpty() && policy == ComparisonPolicy.TRIM_WHITESPACES) {
        lineFragments = ComparisonManager.getInstance().compareLines(
          origText, updatedText, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
      }
      val diff = squash(lineFragments)
      val diffs = diff.mapNotNull { fragment ->
        val start = getOffset(updatedText, fragment.startLine2)
        val end = getOffset(updatedText, fragment.endLine2)
        if (start > end) return@mapNotNull null

        val origStart = getOffset(origText, fragment.startLine1)
        val origEnd = getOffset(origText, fragment.endLine1)
        if (origStart > origEnd) return@mapNotNull null

        val newText = updatedText.substring(start, end).trimStart('\n').trimEnd('\n').trimIndent()
        val oldText = origText.substring(origStart, origEnd).trimStart('\n').trimEnd('\n').trimIndent()

        val deleted = newText.isBlank()
        val shift = if (normalDiff) fragment.startLine1 else -1
        if (deleted) {
          if (oldText.isBlank()) return@mapNotNull null
          return@mapNotNull DiffInfo(fileType, oldText, shift, fragment.endLine1 - fragment.startLine1,
                                     listOf(Fragment(HighlightingType.DELETED, 0, oldText.length)))
        }

        var wordFragments: List<Fragment> = listOf()
        if (fragment.endLine2 - fragment.startLine2 == 1 && fragment.endLine1 - fragment.startLine1 == 1 || oldText.contains(newText)) {
          val comparisonManager = ComparisonManager.getInstance()
          val words = comparisonManager.compareWords(oldText, newText, ComparisonPolicy.IGNORE_WHITESPACES,
                                                     DumbProgressIndicator.INSTANCE)
          if (words.all { word -> word.startOffset2 == word.endOffset2 }) {
            // only deleted
            return@mapNotNull DiffInfo(fileType, oldText, shift, fragment.endLine1 - fragment.startLine1,
                                       words.map { word ->
                                         Fragment(HighlightingType.DELETED, word.startOffset1, word.endOffset1)
                                       })
          }
          else if (words.any { word -> word.startOffset2 == word.endOffset2 }) {
            return@mapNotNull DiffInfo(fileType, newText, shift, fragment.endLine2 - fragment.startLine2,
                                       listOf(Fragment(
                                         HighlightingType.UPDATED,
                                         words.first().startOffset2,
                                         words.last().endOffset2)))
          }
          else {
            wordFragments = words.map { word ->
              val type = if (word.startOffset1 == word.endOffset1) HighlightingType.ADDED else HighlightingType.UPDATED
              Fragment(type, word.startOffset2, word.endOffset2)
            }
          }
        }

        return@mapNotNull DiffInfo(fileType, newText, shift, fragment.endLine2 - fragment.startLine2, wordFragments)
      }
      if (diffs.isNotEmpty()) {
        return IntentionPreviewDiffResult(listOfNotNull(createFileNamePresentation(fileType, fileName)) + diffs, updatedText)
      }
      return IntentionPreviewDiffResult(diffs, updatedText)
    }
    
    @JvmStatic
    fun fromCustomDiff(result: IntentionPreviewInfo.CustomDiff): IntentionPreviewDiffResult {
      return create(
        result.fileType(),
        result.modifiedText(),
        result.originalText(),
        fileName = result.fileName(),
        normalDiff = result.showLineNumbers(),
        policy = ComparisonPolicy.TRIM_WHITESPACES)
    }

    @JvmStatic
    fun fromMultiDiff(info: IntentionPreviewInfo.MultiFileDiff): IntentionPreviewDiffResult {
      val diffs = info.diffs.map { diff -> fromCustomDiff(diff) }
      val diffInfos = diffs.flatMap { diff -> diff.diffs }
      val text = diffs.joinToString("\n----------\n") { diff -> diff.newText }
      return IntentionPreviewDiffResult(diffInfos, text)
    }
  }
}