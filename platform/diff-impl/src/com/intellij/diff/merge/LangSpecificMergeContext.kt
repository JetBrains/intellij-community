// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Stores the information about the environment
 *
 * @see LangSpecificMergeConflictResolver
 */
class LangSpecificMergeContext(val project: Project?, val lineFragmentList: List<MergeLineFragment>, private val fileList: List<PsiFile>, private val lineOffsetList: List<LineOffsets>) {
  fun file(side: ThreeSide): PsiFile {
    return fileList[side.index]
  }

  /**
   * Context contains conflicting ranges (see [lineFragmentList]). This method extracts the [TextRange]s from all fragments by
   * given side. If the fragment is empty, then [TextRange.EMPTY_RANGE] is returned.
   */
  fun lineRanges(side: ThreeSide): List<TextRange> {
    val lineOffsets = lineOffsetList[side.index]
    return lineFragmentList.map {
      val startLine = it.getStartLine(side)
      val endLine = it.getEndLine(side)
      if (startLine == endLine) return@map TextRange.EMPTY_RANGE

      TextRange(lineOffsets.getLineStart(startLine), lineOffsets.getLineEnd(endLine - 1))
    }
  }
}