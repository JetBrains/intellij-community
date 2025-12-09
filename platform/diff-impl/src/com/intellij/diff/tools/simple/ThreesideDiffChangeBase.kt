// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt

abstract class ThreesideDiffChangeBase(var conflictType: MergeConflictType) {
  private val highlighters: MutableList<RangeHighlighter> = mutableListOf()

  @JvmField
  protected val innerHighlighters: MutableList<RangeHighlighter> = mutableListOf()
  @JvmField
  protected val operations: MutableList<DiffGutterOperation> = mutableListOf()

  protected abstract val innerFragments: MergeInnerDifferences?

  @RequiresEdt
  fun destroy() {
    destroyHighlighters()
    destroyInnerHighlighters()
    destroyOperations()
  }

  @RequiresEdt
  protected fun installHighlighters() {
    assert(highlighters.isEmpty())

    createHighlighter(ThreeSide.BASE)
    if (isChange(Side.LEFT)) createHighlighter(ThreeSide.LEFT)
    if (isChange(Side.RIGHT)) createHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  protected fun installInnerHighlighters() {
    assert(innerHighlighters.isEmpty())

    createInnerHighlighter(ThreeSide.BASE)
    if (isChange(Side.LEFT)) createInnerHighlighter(ThreeSide.LEFT)
    if (isChange(Side.RIGHT)) createInnerHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  protected fun destroyHighlighters() {
    for (highlighter in highlighters) {
      highlighter.dispose()
    }
    highlighters.clear()
  }

  @RequiresEdt
  protected fun destroyInnerHighlighters() {
    for (highlighter in innerHighlighters) {
      highlighter.dispose()
    }
    innerHighlighters.clear()
  }

  @RequiresEdt
  protected open fun installOperations() {
  }

  @RequiresEdt
  protected fun destroyOperations() {
    for (operation in operations) {
      operation.dispose()
    }
    operations.clear()
  }

  fun updateGutterActions(force: Boolean) {
    for (operation in operations) {
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

  //
  // Highlighters
  //
  protected fun createHighlighter(side: ThreeSide) {
    val editor = getEditor(side)

    val type = diffType
    val startLine = getStartLine(side)
    val endLine = getEndLine(side)

    val resolved = isResolved(side)
    val ignored = !resolved && innerFragments != null
    val shouldHideWithoutLineNumbers = side == ThreeSide.BASE && !isChange(Side.LEFT) && isChange(Side.RIGHT)
    highlighters.addAll(LineHighlighterBuilder(editor, startLine, endLine, type)
                            .withIgnored(ignored)
                            .withResolved(resolved)
                            .withHideWithoutLineNumbers(shouldHideWithoutLineNumbers)
                            .withHideStripeMarkers(side == ThreeSide.BASE)
                            .done())
  }

  protected fun createInnerHighlighter(side: ThreeSide) {
    if (isResolved(side)) return
    val innerFragments = innerFragments
    if (innerFragments == null) return

    val ranges = innerFragments.get(side)
    if (ranges == null) return

    val editor = getEditor(side)
    val start = DiffUtil.getLinesRange(editor.getDocument(), getStartLine(side), getEndLine(side)).startOffset
    for (fragment in ranges) {
      val innerStart = start + fragment.startOffset
      val innerEnd = start + fragment.endOffset
      innerHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, diffType))
    }
  }
}