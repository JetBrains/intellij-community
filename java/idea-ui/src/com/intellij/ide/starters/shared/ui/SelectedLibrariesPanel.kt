// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.shared.ui

import com.intellij.icons.AllIcons.Actions
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.shared.DependencyState
import com.intellij.ide.starters.shared.DependencyUnavailable
import com.intellij.ide.starters.shared.LibraryInfo
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JLabel

@ApiStatus.Internal
class SelectedLibrariesPanel : JBPanelWithEmptyText(BorderLayout()) {
  private val scrollablePanel: ScrollablePanel = ScrollablePanel(VerticalLayout(UIUtil.DEFAULT_VGAP))
  private val scrollPane = ScrollPaneFactory.createScrollPane(scrollablePanel, true)

  var libraryRemoveListener: ((LibraryInfo) -> Unit)? = null
  var dependencyStateFunction: ((LibraryInfo) -> DependencyState)? = null
  var libraryRemovablePredicate: ((LibraryInfo) -> Boolean)? = null

  init {
    this.background = UIUtil.getListBackground()
    this.border = JBUI.Borders.customLine(JBColor.border(), 1)

    add(scrollPane, BorderLayout.CENTER)

    scrollablePanel.border = JBUI.Borders.empty(5)
    scrollablePanel.background = UIUtil.getListBackground()
    scrollPane.isVisible = false
  }

  fun update(libraries: Collection<LibraryInfo>) {
    scrollablePanel.removeAll()

    for (library in libraries) {
      if (library.isRequired) continue // required are not shown

      val dependencyPanel = BorderLayoutPanel()
      dependencyPanel.background = UIUtil.getListBackground()

      val dependencyLabel = JLabel(library.title)
      dependencyLabel.border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP / 2, UIUtil.DEFAULT_VGAP, 0)

      val dependencyStateFunction = this.dependencyStateFunction
      if (dependencyStateFunction != null) {
        val state = dependencyStateFunction.invoke(library)
        if (state is DependencyUnavailable) {
          dependencyLabel.isEnabled = false
          dependencyLabel.toolTipText = state.message
        }
      }

      dependencyPanel.addToCenter(dependencyLabel)

      val libraryCanBeRemoved = libraryRemovablePredicate?.invoke(library) != false
      if (libraryCanBeRemoved) {
        val removeButton = InplaceButton(IconButton(
          JavaStartersBundle.message("button.tooltip.remove"),
          Actions.Close, Actions.CloseHovered)) {
          libraryRemoveListener?.invoke(library)
        }
        removeButton.setTransform(0, -JBUIScale.scale(2.coerceAtLeast(dependencyLabel.font.size / 15)))
        removeButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        dependencyPanel.addToRight(removeButton)
      }

      scrollablePanel.add(dependencyPanel)
    }
    scrollPane.isVisible = scrollablePanel.componentCount > 0

    scrollablePanel.revalidate()
    scrollPane.revalidate()
    revalidate()
  }
}