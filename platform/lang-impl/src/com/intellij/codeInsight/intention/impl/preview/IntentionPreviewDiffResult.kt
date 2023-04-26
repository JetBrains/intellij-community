// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import one.util.streamex.StreamEx

data class IntentionPreviewDiffResult(val fileType: FileType,
                                      val newText: String,
                                      val origText: String,
                                      val lineFragments: List<LineFragment>,
                                      val normalDiff: Boolean = true,
                                      val fileName: String? = null,
                                      val policy: ComparisonPolicy) : IntentionPreviewInfo {
  fun createDiffs(): List<DiffInfo> {
    val diff = squash(lineFragments)
    val diffs = diff.mapNotNull { fragment ->
      val start = getOffset(newText, fragment.startLine2)
      val end = getOffset(newText, fragment.endLine2)
      if (start > end) return@mapNotNull null

      val origStart = getOffset(origText, fragment.startLine1)
      val origEnd = getOffset(origText, fragment.endLine1)
      if (origStart > origEnd) return@mapNotNull null

      val newText = newText.substring(start, end).trimStart('\n').trimEnd('\n').trimIndent()
      val oldText = origText.substring(origStart, origEnd).trimStart('\n').trimEnd('\n').trimIndent()

      val deleted = newText.isBlank()
      if (deleted) {
        if (oldText.isBlank()) return@mapNotNull null
        return@mapNotNull DiffInfo(oldText, fragment.startLine1, fragment.endLine1 - fragment.startLine1,
                                   listOf(Fragment(HighlightingType.DELETED, 0, oldText.length)))
      }

      var wordFragments: List<Fragment> = listOf()
      if (fragment.endLine2 - fragment.startLine2 == 1 && fragment.endLine1 - fragment.startLine1 == 1) {
        val comparisonManager = ComparisonManager.getInstance()
        val words = comparisonManager.compareWords(oldText, newText, ComparisonPolicy.IGNORE_WHITESPACES,
                                                   DumbProgressIndicator.INSTANCE)
        if (words.all { word -> word.startOffset2 == word.endOffset2 }) {
          // only deleted
          return@mapNotNull DiffInfo(oldText, fragment.startLine1, fragment.endLine1 - fragment.startLine1,
                                     words.map { word ->
                                       Fragment(HighlightingType.DELETED, word.startOffset1, word.endOffset1)
                                     })
        }
        else if (words.any { word -> word.startOffset2 == word.endOffset2 }) {
          return@mapNotNull DiffInfo(newText, fragment.startLine1, fragment.endLine2 - fragment.startLine2,
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

      return@mapNotNull DiffInfo(newText, fragment.startLine1, fragment.endLine2 - fragment.startLine2, wordFragments)
    }
    return diffs
  }

  enum class HighlightingType { ADDED, UPDATED, DELETED }

  data class Fragment(val type: HighlightingType, val start: Int, val end: Int)

  data class DiffInfo(val fileText: String,
                      val startLine: Int,
                      val length: Int,
                      val fragments: List<Fragment>)

  private fun squash(lines: List<LineFragment>): List<LineFragment> = StreamEx.of(lines)
    .collapse({ f1, f2 -> f2.startLine1 - f1.endLine1 == 1 && f2.startLine2 - f1.endLine2 == 1 },
              { f1, f2 ->
                LineFragmentImpl(f1.startLine1, f2.endLine1, f1.startLine2, f2.endLine2,
                                 f1.startOffset1, f2.endOffset1, f1.startOffset2, f2.endOffset2)
              }).toList()

  private fun getOffset(fileText: String, lineNumber: Int): Int {
    return StringUtil.lineColToOffset(fileText, lineNumber, 0).let { pos -> if (pos == -1) fileText.length else pos }
  }

  companion object {
    @JvmStatic
    fun fromCustomDiff(result: IntentionPreviewInfo.CustomDiff): IntentionPreviewDiffResult {
      return IntentionPreviewDiffResult(
        result.fileType(),
        result.modifiedText(),
        result.originalText(),
        ComparisonManager.getInstance().compareLines(result.originalText(), result.modifiedText(),
                                                     ComparisonPolicy.TRIM_WHITESPACES, DumbProgressIndicator.INSTANCE),
        fileName = result.fileName(),
        normalDiff = false,
        policy = ComparisonPolicy.TRIM_WHITESPACES)
    }
  }
}