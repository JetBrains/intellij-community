// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object TimelineComponentFactory {

  fun <T : TimelineItem> create(
    model: ListModel<T>,
    itemComponentFactory: TimelineItemComponentFactory<T>,
    offset: Int = JBUIScale.scale(20)
  ): JPanel {
    val panel = JPanel(VerticalLayout(offset)).apply {
      isOpaque = false
    }

    model.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          panel.remove(i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          panel.add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          panel.remove(i)
        }
        for (i in e.index0..e.index1) {
          panel.add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
        }
        panel.validate()
        panel.repaint()
      }
    })

    for (i in 0 until model.size) {
      panel.add(itemComponentFactory.createComponent(model.getElementAt(i)), VerticalLayout.FILL_HORIZONTAL, i)
    }
    return panel
  }
}