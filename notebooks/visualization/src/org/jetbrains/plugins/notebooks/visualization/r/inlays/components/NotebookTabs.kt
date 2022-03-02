/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.visualization.r.VisualizationBundle
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * This component "installs" into bottom area of given FileEditor and allows us to add tabs with custom components under editor.
 */
class NotebookTabs private constructor(private val editor: BorderLayoutPanel) : JPanel() {

  companion object {

    fun install(editor: FileEditor): NotebookTabs? {

      val component = editor.component

      if (component !is BorderLayoutPanel)
        return null

      val componentLayout = component.layout as? BorderLayout
      if (componentLayout == null) {
        return null
      }

      val bottomComponent = componentLayout.getLayoutComponent(BorderLayout.SOUTH)

      when (bottomComponent) {
        null -> return NotebookTabs(component)
        is NotebookTabs -> return bottomComponent
        else -> return null
      }
    }
  }

  private val tabs = HashMap<JToggleButton, Component>()

  init {
    editor.addToBottom(this)
    val center = (editor.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
    addTab(VisualizationBundle.message("notebook.tabs.code.title"), center)
  }

  fun addTab(@Nls name: String, page: Component) {
    val tab = JToggleButton(name)

    val action = {
      val currentCenter = (editor.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
      if (currentCenter != page) {
        editor.remove(currentCenter)
        editor.add(page, BorderLayout.CENTER)

        editor.repaint()
      }
    }

    tab.addActionListener { action.invoke() }

    tabs[tab] = page

    action.invoke()

    add(tab)
  }
}