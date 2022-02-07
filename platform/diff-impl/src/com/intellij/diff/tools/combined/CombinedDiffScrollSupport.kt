// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.ScrollingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.Point
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

enum class ScrollPolicy {
  DIFF_BLOCK, DIFF_CHANGE
}

internal class CombinedDiffScrollSupport(project: Project?, private val viewer: CombinedDiffViewer) {

  internal val currentPrevNextIterable = CombinedDiffPrevNextDifferenceIterable()
  internal val blockIterable = CombinedDiffPrevNextBlocksIterable()

  private val combinedEditorsScrollingModel = ScrollingModelImpl(CombinedEditorsScrollingModelHelper(project, viewer))

  fun scroll(index: Int, block: CombinedDiffBlock<*>, scrollPolicy: ScrollPolicy){
    val isEditorBased = viewer.diffViewers[block.id]?.isEditorBased ?: false
    if (scrollPolicy == ScrollPolicy.DIFF_BLOCK || !isEditorBased) {
      scrollToDiffBlock(index)
    }
    else if (scrollPolicy == ScrollPolicy.DIFF_CHANGE) {
      scrollToDiffChangeWithCaret()
    }
  }

  private fun scrollToDiffChangeWithCaret() {
    if (viewer.getCurrentDiffViewer().isEditorBased) { //avoid scrolling for non editor based viewers
      combinedEditorsScrollingModel.scrollToCaret(ScrollType.CENTER)
    }
  }

  private fun scrollToDiffBlock(index: Int) {
    if (viewer.diffBlocksPositions.values.contains(index)) {
      viewer.contentPanel.components.getOrNull(index)?.bounds?.let(viewer.contentPanel::scrollRectToVisible)
    }
  }

  internal inner class CombinedDiffPrevNextDifferenceIterable : PrevNextDifferenceIterable {

    override fun canGoNext(): Boolean {
      return viewer.getDifferencesIterable()?.canGoNext() == true
    }

    override fun canGoPrev(): Boolean {
      return viewer.getDifferencesIterable()?.canGoPrev() == true
    }

    override fun goNext() {
      viewer.getDifferencesIterable()?.goNext()
      scrollToDiffChangeWithCaret()
    }

    override fun goPrev() {
      viewer.getDifferencesIterable()?.goPrev()
      scrollToDiffChangeWithCaret()
    }
  }

  internal inner class CombinedDiffPrevNextBlocksIterable : PrevNextDifferenceIterable {
    var index = 0

    override fun canGoNext(): Boolean = index < viewer.diffBlocks.size - 1

    override fun canGoPrev(): Boolean = index > 0

    override fun goNext() {
      index++
    }

    override fun goPrev() {
      index--
    }
  }

  private inner class CombinedEditorsScrollingModelHelper(project: Project?, disposable: Disposable) :
    ScrollingModel.Supplier, ScrollingModel.ScrollingHelper, Disposable {

    private val dummyEditor: Editor //needed for ScrollingModelImpl initialization

    init {
      dummyEditor = DiffUtil.createEditor(EditorFactory.getInstance().createDocument(""), project, true, true)
      Disposer.register(disposable, this)
    }

    override fun getEditor(): Editor = viewer.getDiffViewer(blockIterable.index)?.editor ?: dummyEditor

    override fun getScrollPane(): JScrollPane = viewer.scrollPane

    override fun getScrollingHelper(): ScrollingModel.ScrollingHelper = this

    override fun calculateScrollingLocation(editor: Editor, pos: VisualPosition): Point {
      val targetLocationInEditor = editor.visualPositionToXY(pos)
      return SwingUtilities.convertPoint(editor.component, targetLocationInEditor, scrollPane.viewport.view)
    }

    override fun calculateScrollingLocation(editor: Editor, pos: LogicalPosition): Point {
      val targetLocationInEditor = editor.logicalPositionToXY(pos)
      return SwingUtilities.convertPoint(editor.component, targetLocationInEditor, scrollPane.viewport.view)
    }

    override fun dispose() {
      EditorFactory.getInstance().releaseEditor(dummyEditor)
    }
  }
}
