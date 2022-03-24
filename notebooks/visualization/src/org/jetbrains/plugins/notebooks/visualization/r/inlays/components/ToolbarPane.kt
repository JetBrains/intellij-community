/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import org.jetbrains.plugins.notebooks.visualization.r.ui.UiCustomizer
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * ToolbarPane - a special component consisting of two parts which are
 * setDataComponent() - used for displaying some output and should fill the major part of ToolbarPane, is aligned to the left
 * setToolbarComponent() - typically occupies the small area to the right and contains the button that shows the output actions menu
 */
class ToolbarPane(val inlayOutput: InlayOutput) : JPanel(BorderLayout()) {
  private var mainPanel: JPanel? = null

  var dataComponent: JComponent? = null
    set(value) {
      field = value
      updateMainComponent()
      updateChildrenBounds()
    }

  var progressComponent: JComponent? = null
    set(value) {
      field = value
      updateMainComponent()
      updateChildrenBounds()
      UiCustomizer.instance.toolbarPaneProgressComponentChanged(this, value)
    }

  var toolbarComponent: JComponent? = null
    set(value) {
      field = value
      updateMainComponent()
      updateChildrenBounds()
      UiCustomizer.instance.toolbarPaneToolbarComponentChanged(this, value)
    }

  private fun updateMainComponent() {
    if (mainPanel == null) {
      mainPanel = JPanel(BorderLayout()).also { mainPanel ->
        UiCustomizer.instance.toolbarPaneMainPanelCreated(this, mainPanel)
        add(NotebookInlayMouseListener.wrapPanel(mainPanel, inlayOutput.editor), BorderLayout.CENTER)
      }
    }
    mainPanel?.let { main ->
      main.removeAll()
      progressComponent?.let { progress ->
        main.add(progress, BorderLayout.PAGE_START)
      }
      dataComponent?.let { central ->
        main.add(central, BorderLayout.CENTER)
      }
      toolbarComponent?.let { toolbar ->
        main.add(toolbar, BorderLayout.LINE_END)
      }
    }
  }

  fun updateChildrenBounds() {
    mainPanel?.setBounds(0, 0, width, height)
    val progressBarWidth = if (progressComponent != null) PROGRESS_BAR_DEFAULT_WIDTH else 0
    toolbarComponent?.setBounds(width - toolbarComponent!!.preferredSize.width, progressBarWidth, toolbarComponent!!.preferredSize.width,
                                toolbarComponent!!.preferredSize.height)
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x, y, width, height)

    updateChildrenBounds()
  }
}