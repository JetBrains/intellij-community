// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Editor
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
class TextMergeChange(
  val index: Int,
  val isImportChange: Boolean,
  val fragment: MergeLineFragment,
  conflictType: MergeConflictType,
  private val model: MergeModelBase<State>,
) : ThreesideDiffChangeBase(conflictType) {

  private val resolved: BooleanArray = BooleanArray(2)

  var isOnesideAppliedConflict: Boolean = false
    private set

  @get:ApiStatus.Internal
  var isResolvedWithAI: Boolean = false
    private set

  @RequiresEdt
  fun setResolved(side: Side, value: Boolean) {
    resolved[side.index] = value
  }

  val isResolved: Boolean
    get() = resolved[0] && resolved[1]

  fun isResolved(side: Side): Boolean = side.select(resolved)

  fun markOnesideAppliedConflict() {
    isOnesideAppliedConflict = true
  }

  @ApiStatus.Internal
  fun markChangeResolvedWithAI() {
    isResolvedWithAI = true
  }

  override fun isResolved(side: ThreeSide): Boolean = when (side) {
    ThreeSide.LEFT -> isResolved(Side.LEFT)
    ThreeSide.BASE -> isResolved
    ThreeSide.RIGHT -> isResolved(Side.RIGHT)
  }

  val resultStartLine: Int
    get() = model.getLineStart(index)

  val resultEndLine: Int
    get() = model.getLineEnd(index)

  override fun getStartLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return resultStartLine
    return fragment.getStartLine(side)
  }

  override fun getEndLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return resultEndLine
    return fragment.getEndLine(side)
  }

  override val diffType: TextDiffType
    get() {
      val baseType = super.diffType
      if (!isResolvedWithAI) return baseType

      return AIResolvedDiffType(baseType)
    }

  //
  // State
  //
  fun storeState(): State {
    return State(
      index,
      resultStartLine,
      resultEndLine,

      resolved[0],
      resolved[1],

      isOnesideAppliedConflict,
      isResolvedWithAI)
  }

  fun restoreState(state: State) {
    resolved[0] = state.resolved1
    resolved[1] = state.resolved2

    isOnesideAppliedConflict = state.onesideAppliedConflict
    isResolvedWithAI = state.isResolvedByAI
  }

  @ApiStatus.Internal
  fun resetState() {
    resolved[0] = false
    resolved[1] = false
    isOnesideAppliedConflict = false
    isResolvedWithAI = false
  }

  class State(
    index: Int,
    startLine: Int,
    endLine: Int,
    val resolved1: Boolean,
    val resolved2: Boolean,
    val onesideAppliedConflict: Boolean,
    val isResolvedByAI: Boolean,
  ) : MergeModelBase.State(index, startLine, endLine)

  private class AIResolvedDiffType(private val baseType: TextDiffType) : TextDiffType by baseType {
    override fun getColor(editor: Editor?): Color = JBColor(0x834DF0, 0xA571E6)
  }
}
