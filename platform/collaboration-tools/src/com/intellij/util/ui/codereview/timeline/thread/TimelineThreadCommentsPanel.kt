// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline.thread

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClickListener
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.SingleValueModel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.timeline.thread.TimelineThreadCommentsPanel.Companion.FOLD_THRESHOLD
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
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
  offset: Int = JBUI.scale(8)
) : BorderLayoutPanel() {
  companion object {
    private const val FOLD_THRESHOLD = 3
  }

  private val foldModel = SingleValueModelImpl(true)

  private val unfoldButtonPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.emptyLeft(30)

    addToLeft(UnfoldButton(foldModel).apply {
      foreground = UIUtil.getLabelForeground()
      font = UIUtil.getButtonFont()
    })
  }

  private val foldablePanel = FoldablePanel(unfoldButtonPanel, offset).apply {
    for (i in 0 until commentsModel.size) {
      addComponent(commentComponentFactory(commentsModel.getElementAt(i)), i)
    }
  }

  init {
    isOpaque = false
    addToCenter(foldablePanel)
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)

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

    foldModel.addValueUpdatedListener { updateFolding(it) }
    updateFolding(true)
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
  private class FoldablePanel(private val unfoldButton: JComponent, offset: Int) : JPanel(VerticalLayout(offset)) {
    init {
      isOpaque = false
      add(unfoldButton, VerticalLayout.FILL_HORIZONTAL)
    }

    fun addComponent(component: JComponent, index: Int) {
      remove(unfoldButton)
      add(component, VerticalLayout.FILL_HORIZONTAL, index)
      add(unfoldButton, VerticalLayout.FILL_HORIZONTAL, 1)
    }

    fun removeComponent(index: Int) {
      remove(unfoldButton)
      remove(index)

      val unfoldButtonIndex = if (components.isEmpty()) 0 else 1
      add(unfoldButton, VerticalLayout.FILL_HORIZONTAL, unfoldButtonIndex)
    }

    fun getModelComponent(modelIndex: Int): Component =
      if (modelIndex == 0) {
        getComponent(0)
      }
      else {
        getComponent(modelIndex + 1)
      }
  }

  private class UnfoldButton(model: SingleValueModel<Boolean>) : JComponent() {
    init {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          model.value = !model.value
          return true
        }
      }.installOn(this)
    }

    override fun getPreferredSize(): Dimension {
      return Dimension(JBUI.scale(30), font.size)
    }

    override fun paintComponent(g: Graphics) {
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, insets)

      val g2 = g as Graphics2D

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      val arc = DarculaUIUtil.BUTTON_ARC.float
      g2.color = background
      g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc))

      g2.color = foreground
      g2.font = font
      val line = StringUtil.ELLIPSIS
      val lineBounds = g2.fontMetrics.getStringBounds(line, g2)
      val x = (rect.width - lineBounds.width) / 2
      val y = (rect.height + lineBounds.y) / 2 - lineBounds.y / 2
      g2.drawString(line, x.toFloat(), y.toFloat())
    }
  }
}