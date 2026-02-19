// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.thread

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.timeline.thread.TimelineThreadCommentsPanel.Companion.FOLD_THRESHOLD
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * Shows thread items with folding if there are more than [FOLD_THRESHOLD] of them
 */
class TimelineThreadCommentsPanel<T>(
  private val commentsModel: ListModel<T>,
  private val commentComponentFactory: (T) -> JComponent,
  offset: Int = JBUI.scale(8),
  private val foldButtonOffset: Int = 30
) : BorderLayoutPanel(), UiDataProvider {

  val foldModel = SingleValueModel(true)
  private val collapsedCountModel: SingleValueModel<Int> = SingleValueModel(commentsModel.size - FOLD_THRESHOLD - 1)

  init {
    commentsModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent?) {
        collapsedCountModel.value = commentsModel.size - FOLD_THRESHOLD - 1
      }

      override fun intervalRemoved(e: ListDataEvent?) {
        collapsedCountModel.value = commentsModel.size - FOLD_THRESHOLD - 1
      }

      override fun contentsChanged(e: ListDataEvent?) {
        collapsedCountModel.value = commentsModel.size - FOLD_THRESHOLD - 1
      }
    })
  }

  private val unfoldButtonPanel = createUnfoldPanel(collapsedCountModel)

  private val foldablePanel = FoldablePanel(unfoldButtonPanel, offset).apply {
    for (i in 0 until commentsModel.size) {
      addComponent(commentComponentFactory(commentsModel.getElementAt(i)), i)
    }
  }

  init {
    isOpaque = false
    addToCenter(foldablePanel)

    commentsModel.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          foldablePanel.removeComponent(i)
        }
        updateFolding(foldModel.value)
        foldablePanel.revalidate()
        foldablePanel.repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          foldablePanel.addComponent(commentComponentFactory(commentsModel.getElementAt(i)), i)
        }
        foldablePanel.revalidate()
        foldablePanel.repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          foldablePanel.removeComponent(i)
        }
        for (i in e.index0..e.index1) {
          foldablePanel.addComponent(commentComponentFactory(commentsModel.getElementAt(i)), i)
        }
        foldablePanel.validate()
        foldablePanel.repaint()
      }
    })

    foldModel.addListener { updateFolding(it) }
    updateFolding(foldModel.value)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink.setNull(CommonDataKeys.EDITOR)
  }

  private fun updateFolding(folded: Boolean) {
    val shouldFold = folded && commentsModel.size > FOLD_THRESHOLD
    unfoldButtonPanel.isVisible = shouldFold

    if (commentsModel.size == 0) {
      return
    }

    foldablePanel.getModelComponent(0).isVisible = true
    foldablePanel.getModelComponent(commentsModel.size - 1).isVisible = true

    for (i in 1 until commentsModel.size - 1) {
      foldablePanel.getModelComponent(i).isVisible = !shouldFold
    }
  }

  /**
   * [FoldablePanel] hides [unfoldButton] and allows to use this panel like it doesn't contain it
   */
  private class FoldablePanel(private val unfoldButton: JComponent, offset: Int) : JPanel(ListLayout.vertical(offset)) {
    init {
      isOpaque = false
      add(unfoldButton)
    }

    fun addComponent(component: JComponent, index: Int) {
      remove(unfoldButton)
      add(component, null, index)
      add(unfoldButton, null, 1)
    }

    fun removeComponent(index: Int) {
      remove(unfoldButton)
      remove(index)

      val unfoldButtonIndex = if (components.isEmpty()) 0 else 1
      add(unfoldButton, null, unfoldButtonIndex)
    }

    fun getModelComponent(modelIndex: Int): Component =
      if (modelIndex == 0) {
        getComponent(0)
      }
      else {
        getComponent(modelIndex + 1)
      }
  }

  private fun createUnfoldPanel(foldedCount: SingleValueModel<Int>): JComponent =
    BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyLeft(foldButtonOffset)

      addToLeft(createUnfoldComponent(foldedCount) {
        foldModel.value = !foldModel.value
      })
    }

  companion object {
    const val FOLD_THRESHOLD = 3

    const val UNFOLD_BUTTON_VERTICAL_GAP = 18

    fun createUnfoldComponent(foldedCount: Int, actionListener: (ActionEvent) -> Unit): JComponent =
      createUnfoldComponent(SingleValueModel(foldedCount), actionListener)

    private fun createUnfoldComponent(foldedCount: SingleValueModel<Int>, actionListener: (ActionEvent) -> Unit): JComponent {
      return ActionLink("", actionListener).apply {
        icon = AllIcons.Actions.MoreHorizontal
      }.apply {
        border = JBUI.Borders.empty(UNFOLD_BUTTON_VERTICAL_GAP, 0)

        foldedCount.addAndInvokeListener {
          text = CollaborationToolsBundle.message("review.thread.more.replies", it)
        }
      }
    }
  }
}