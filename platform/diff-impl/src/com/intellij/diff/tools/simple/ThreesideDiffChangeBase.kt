// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.diff.util.ThreeSide

abstract class ThreesideDiffChangeBase(val conflictType: MergeConflictType) {
  abstract fun getStartLine(side: ThreeSide): Int

  abstract fun getEndLine(side: ThreeSide): Int

  abstract fun isResolved(side: ThreeSide): Boolean

  open val diffType: TextDiffType
    get() = DiffUtil.getDiffType(conflictType)

  val isConflict: Boolean
    get() = conflictType.type == MergeConflictType.Type.CONFLICT

  fun isChange(side: Side): Boolean {
    return conflictType.isChange(side)
  }

  fun isChange(side: ThreeSide): Boolean {
    return conflictType.isChange(side)
  }
}