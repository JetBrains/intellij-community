// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt

abstract class ThreesideDiffChangeBase(var conflictType: MergeConflictType) {
  protected val myHighlighters: MutableList<RangeHighlighter> = ArrayList<RangeHighlighter>()
  protected val myInnerHighlighters: MutableList<RangeHighlighter> = ArrayList<RangeHighlighter>()
  protected val myOperations: MutableList<DiffGutterOperation> = ArrayList<DiffGutterOperation>()

  @RequiresEdt
  fun destroy() {
    destroyHighlighters()
    destroyInnerHighlighters()
    destroyOperations()
  }

  @RequiresEdt
  protected fun installHighlighters() {
    assert(myHighlighters.isEmpty())

    createHighlighter(ThreeSide.BASE)
    if (isChange(Side.LEFT)) createHighlighter(ThreeSide.LEFT)
    if (isChange(Side.RIGHT)) createHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  protected fun installInnerHighlighters() {
    assert(myInnerHighlighters.isEmpty())

    createInnerHighlighter(ThreeSide.BASE)
    if (isChange(Side.LEFT)) createInnerHighlighter(ThreeSide.LEFT)
    if (isChange(Side.RIGHT)) createInnerHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  protected fun destroyHighlighters() {
    for (highlighter in myHighlighters) {
      highlighter.dispose()
    }
    myHighlighters.clear()
  }

  @RequiresEdt
  protected fun destroyInnerHighlighters() {
    for (highlighter in myInnerHighlighters) {
      highlighter.dispose()
    }
    myInnerHighlighters.clear()
  }

  @RequiresEdt
  protected open fun installOperations() {
  }

  @RequiresEdt
  protected fun destroyOperations() {
    for (operation in myOperations) {
      operation.dispose()
    }
    myOperations.clear()
  }

  fun updateGutterActions(force: Boolean) {
    for (operation in myOperations) {
      operation.update(force)
    }
  }

  //
  // Getters
  //
  abstract fun getStartLine(side: ThreeSide): Int

  abstract fun getEndLine(side: ThreeSide): Int

  abstract fun isResolved(side: ThreeSide): Boolean

  protected abstract fun getEditor(side: ThreeSide): Editor

  protected abstract val innerFragments: MergeInnerDifferences?

  open val diffType: TextDiffType
    get() = DiffUtil.getDiffType(this.conflictType)

  val isConflict: Boolean
    get() = conflictType.type == MergeConflictType.Type.CONFLICT

  fun isChange(side: Side): Boolean {
    return conflictType.isChange(side)
  }

  fun isChange(side: ThreeSide): Boolean {
    return conflictType.isChange(side)
  }

  //
  // Highlighters
  //
  protected fun createHighlighter(side: ThreeSide) {
    val editor = getEditor(side)

    val type = this.diffType
    val startLine = getStartLine(side)
    val endLine = getEndLine(side)

    val resolved = isResolved(side)
    val ignored = !resolved && this.innerFragments != null
    val shouldHideWithoutLineNumbers = side == ThreeSide.BASE && !isChange(Side.LEFT) && isChange(Side.RIGHT)
    myHighlighters.addAll(
      LineHighlighterBuilder(editor, startLine, endLine, type)
        .withIgnored(ignored)
        .withResolved(resolved)
        .withHideWithoutLineNumbers(shouldHideWithoutLineNumbers)
        .withHideStripeMarkers(side == ThreeSide.BASE)
        .done()
    )
  }

  protected fun createInnerHighlighter(side: ThreeSide) {
    if (isResolved(side)) return
    val innerFragments = this.innerFragments
    if (innerFragments == null) return

    val ranges = innerFragments.get(side)
    if (ranges == null) return

    val editor = getEditor(side)
    val start = DiffUtil.getLinesRange(editor.getDocument(), getStartLine(side), getEndLine(side)).getStartOffset()
    for (fragment in ranges) {
      val innerStart = start + fragment.getStartOffset()
      val innerEnd = start + fragment.getEndOffset()
      myInnerHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, this.diffType))
    }
  }
}