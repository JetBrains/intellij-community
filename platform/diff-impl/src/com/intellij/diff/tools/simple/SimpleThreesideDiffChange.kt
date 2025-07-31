// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.ThreeSide


class SimpleThreesideDiffChange(
  fragment: MergeLineFragment,
  conflictType: MergeConflictType,
) : ThreesideDiffChangeBase(conflictType) {
  private val lineStarts = IntArray(3)
  private val lineEnds = IntArray(3)

  var isValid: Boolean = true
    private set

  init {
    for (side in ThreeSide.entries) {
      lineStarts[side.index] = fragment.getStartLine(side)
      lineEnds[side.index] = fragment.getEndLine(side)
    }
  }

  override fun getStartLine(side: ThreeSide): Int = side.select(myLineStarts)
  override fun getEndLine(side: ThreeSide): Int = side.select(myLineEnds)

  override fun isResolved(side: ThreeSide): Boolean = false

  fun markInvalid() {
    isValid = false
  }

  //
  // Shift
  //
  fun processChange(oldLine1: Int, oldLine2: Int, shift: Int, side: ThreeSide): Boolean {
    val line1 = getStartLine(side)
    val line2 = getEndLine(side)
    val sideIndex = side.index

    val newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift)
    lineStarts[sideIndex] = newRange.startLine
    lineEnds[sideIndex] = newRange.endLine

    return newRange.damaged
  }
}