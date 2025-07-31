// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt

abstract class DiffViewerHighlighters(
  protected open val change: ThreesideDiffChangeBase,
  protected open val innerFragments: MergeInnerDifferences?,
  protected val editorProvider: (ThreeSide) -> EditorEx,
) {
  protected val highlighters: MutableList<RangeHighlighter> = mutableListOf()
  protected val innerHighlighters: MutableList<RangeHighlighter> = mutableListOf()

  protected val operations: MutableList<DiffGutterOperation> = mutableListOf()

  @RequiresEdt
  protected fun installHighlighters() {
    assert(highlighters.isEmpty())

    createHighlighter(ThreeSide.BASE)
    if (change.isChange(Side.LEFT)) createHighlighter(ThreeSide.LEFT)
    if (change.isChange(Side.RIGHT)) createHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  protected fun installInnerHighlighters() {
    assert(innerHighlighters.isEmpty())

    createInnerHighlighter(ThreeSide.BASE)
    if (change.isChange(Side.LEFT)) createInnerHighlighter(ThreeSide.LEFT)
    if (change.isChange(Side.RIGHT)) createInnerHighlighter(ThreeSide.RIGHT)
  }

  @RequiresEdt
  abstract fun installOperations()

  @RequiresEdt
  fun destroy() {
    destroyHighlighters()
    destroyInnerHighlighters()
    destroyOperations()
  }

  @RequiresEdt
  protected fun destroyHighlighters() {
    disposeAndClear(highlighters, RangeHighlighter::dispose)
  }

  @RequiresEdt
  fun destroyInnerHighlighters() {
    disposeAndClear(innerHighlighters, RangeHighlighter::dispose)
  }

  @RequiresEdt
  protected fun destroyOperations() {
    disposeAndClear(operations, DiffGutterOperation::dispose)
  }

  @RequiresEdt
  open fun reinstallAll() {
    destroyHighlighters()
    installHighlighters()

    destroyInnerHighlighters()
    installInnerHighlighters()

    destroyOperations()
    installOperations()
  }

  protected fun createInnerHighlighter(side: ThreeSide) {
    if (change.isResolved(side)) return
    val innerFragments = innerFragments ?: return

    val ranges = innerFragments.get(side)
    if (ranges == null) return

    val editor = editorProvider(side)
    val start = DiffUtil.getLinesRange(editor.getDocument(), change.getStartLine(side), change.getEndLine(side)).startOffset
    for (fragment in ranges) {
      val innerStart = start + fragment.startOffset
      val innerEnd = start + fragment.endOffset
      innerHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, change.diffType))
    }
  }

  protected fun createHighlighter(side: ThreeSide) {
    val editor = editorProvider(side)

    val type = change.diffType
    val startLine = change.getStartLine(side)
    val endLine = change.getEndLine(side)

    val resolved = change.isResolved(side)
    val ignored = !resolved && innerFragments != null
    val shouldHideWithoutLineNumbers = side == ThreeSide.BASE && !change.isChange(Side.LEFT) && change.isChange(Side.RIGHT)
    highlighters.addAll(DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
                          .withIgnored(ignored)
                          .withResolved(resolved)
                          .withHideWithoutLineNumbers(shouldHideWithoutLineNumbers)
                          .withHideStripeMarkers(side == ThreeSide.BASE)
                          .done())
  }

  companion object {
    private fun <S> disposeAndClear(collection: MutableCollection<S>, disposer: (S) -> Unit) {
      for (element in collection) {
        disposer(element)
      }
      collection.clear()
    }
  }
}