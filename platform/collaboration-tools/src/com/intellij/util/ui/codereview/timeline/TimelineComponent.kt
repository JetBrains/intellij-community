// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

open class TimelineComponent<in T : TimelineItem>(
  private val model: ListModel<T>,
  protected val itemComponentFactory: TimelineItemComponentFactory<T>,
  private val title: JComponent? = null,
  offset: Int = JBUIScale.scale(20)
) : JPanel(VerticalLayout(offset)) {

  init {
    isOpaque = false

    model.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          remove(i.viewIndex())
        }
        revalidate()
        repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i.viewIndex())
        }
        revalidate()
        repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          remove(i.viewIndex())
        }
        for (i in e.index0..e.index1) {
          add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i.viewIndex())
        }
        validate()
        repaint()
      }
    })

    if (title != null) {
      add(title, VerticalLayout.FILL_HORIZONTAL, 0)
    }
    for (i in 0 until model.size) {
      add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i.viewIndex())
    }
  }

  private fun Int.viewIndex() =
    if (title != null) {
      this + 1
    }
    else {
      this
    }

  final override fun add(comp: Component?, constraints: Any?, index: Int) {
    super.add(comp, constraints, index)
  }
}