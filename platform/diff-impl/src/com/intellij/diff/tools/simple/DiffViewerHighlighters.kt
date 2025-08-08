// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class DiffViewerHighlighters(
  protected open val change: ThreesideDiffChangeBase,
  protected open var innerFragments: MergeInnerDifferences?,
  protected val editorProvider: (ThreeSide) -> EditorEx,
) {

  private val _highlighters: MutableList<RangeHighlighter> = mutableListOf()
  private val _innerHighlighters: MutableList<RangeHighlighter> = mutableListOf()
  private val _operations: MutableList<DiffGutterOperation> = mutableListOf()

  protected val highlighters: List<RangeHighlighter> get() = _highlighters
  protected val innerHighlighters: List<RangeHighlighter> get() = _innerHighlighters
  protected val operations: List<DiffGutterOperation> get() = _operations

  @RequiresEdt
  protected fun addHighlighters(highlighters: Collection<RangeHighlighter>) {
    _highlighters.addAll(highlighters)
  }

  @RequiresEdt
  protected fun addHighlighter(highlighter: RangeHighlighter) {
    _highlighters.add(highlighter)
  }

  @RequiresEdt
  protected fun addInnerHighlighters(highlighters: Collection<RangeHighlighter>) {
    _innerHighlighters.addAll(highlighters)
  }

  @RequiresEdt
  protected fun addOperation(operation: DiffGutterOperation?) {
    operation?.let { _operations.add(it) }
  }

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
    disposeAndClear(_highlighters, RangeHighlighter::dispose)
  }

  @RequiresEdt
  fun destroyInnerHighlighters() {
    disposeAndClear(_innerHighlighters, RangeHighlighter::dispose)
  }

  @RequiresEdt
  protected fun destroyOperations() {
    disposeAndClear(_operations, DiffGutterOperation::dispose)
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

  private fun createInnerHighlighter(side: ThreeSide) {
    if (change.isResolved(side)) return
    val innerFragments = innerFragments ?: return

    val ranges = innerFragments.get(side) ?: return

    val editor = editorProvider(side)
    val start = DiffUtil.getLinesRange(editor.getDocument(), change.getStartLine(side), change.getEndLine(side)).startOffset
    for (fragment in ranges) {
      val innerStart = start + fragment.startOffset
      val innerEnd = start + fragment.endOffset
      addInnerHighlighters(DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, change.diffType))
    }
  }

  private fun createHighlighter(side: ThreeSide) {
    val editor = editorProvider(side)

    val type = change.diffType
    val startLine = change.getStartLine(side)
    val endLine = change.getEndLine(side)

    val resolved = change.isResolved(side)
    val ignored = !resolved && innerFragments != null
    val shouldHideWithoutLineNumbers = side == ThreeSide.BASE && !change.isChange(Side.LEFT) && change.isChange(Side.RIGHT)
    addHighlighters(DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
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