// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ListUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBList.createDefaultListModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

internal class ExternalProjectSelector(
  property: ObservableMutableProperty<DependencyAnalyzerProject?>,
  externalProjects: List<DependencyAnalyzerProject>,
  private val iconProvider: ExternalSystemIconProvider
) : JPanel() {
  init {
    val dropDownLink = ExternalProjectDropDownLink(property, externalProjects)
      .apply { border = JBUI.Borders.empty(BORDER, ICON_TEXT_GAP / 2, BORDER, BORDER) }
    val label = JLabel(iconProvider.projectIcon)
      .apply { preferredSize = Dimension(iconProvider.projectIcon.iconWidth + BORDER, iconProvider.projectIcon.iconHeight) }
      .apply { border = JBUI.Borders.empty(BORDER, BORDER, BORDER, ICON_TEXT_GAP / 2) }
      .apply { labelFor = dropDownLink }

    layout = ListLayout.horizontal(0)
    border = JBUI.Borders.empty()
    add(label)
    add(dropDownLink)
  }

  private fun createPopup(externalProjects: List<DependencyAnalyzerProject>, onChange: (DependencyAnalyzerProject) -> Unit): JBPopup {
    val content = ExternalProjectPopupContent(externalProjects)
    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content, null)
      .setRequestFocus(true)
      .createPopup()
      .apply {
        // Add a MouseListener to handle mouse click events
        content.list.addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            val selectedProject = content.list.selectedValue
            if (selectedProject != null) {
              // Handle the selected project
              onChange(selectedProject)
              closeOk(e)
            }
          }
        })

        // Add a KeyListener to handle enter key events
        content.list.addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
              val selectedProject = content.list.selectedValue
              if (selectedProject != null) {
                // Handle the selected project
                onChange(selectedProject)
                closeOk(e)
              }
            }
          }
        })

        addListener(object : JBPopupListener {
          override fun beforeShown(event: LightweightWindowEvent) {
            SwingUtilities.invokeLater {
              content.filterField.requestFocusInWindow()
            }
          }
        })
      }
  }

  private inner class ExternalProjectPopupContent(externalProject: List<DependencyAnalyzerProject>) : JPanel() {
    val list: JBList<DependencyAnalyzerProject>
    val filterField = SearchTextField(false)
    var listModel: DefaultListModel<DependencyAnalyzerProject>

    init {
      listModel = createDefaultListModel(externalProject)
      list = JBList<DependencyAnalyzerProject>(listModel)
      list.setVisibleRowCount(15)
      list.border = emptyListBorder()
      list.cellRenderer = ExternalProjectRenderer()
      list.selectionMode = ListSelectionModel.SINGLE_SELECTION
      ListUtil.installAutoSelectOnMouseMove(list)
      setupListPopupPreferredWidth(list)

      // Create a SearchTextField for the user to enter their search query
      filterField.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val filterText = filterField.text
          listModel = createDefaultListModel(externalProject.filter { it.title.contains(filterText, ignoreCase = true) })
          list.model = listModel
          list.selectedIndex = 0
        }
      })

      // Add a KeyListener to handle up, down, and enter key events to delegate to the list to process
      filterField.addKeyboardListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          when (e.keyCode) {
            KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_ENTER -> {
              list.dispatchEvent(e)
            }
          }
        }
      })

      val scrollPane = JBScrollPane(list)
      layout = BorderLayout()
      add(filterField, BorderLayout.NORTH)
      add(scrollPane, BorderLayout.CENTER)

    }
  }

  private inner class ExternalProjectRenderer : ListCellRenderer<DependencyAnalyzerProject?> {
    override fun getListCellRendererComponent(
      list: JList<out DependencyAnalyzerProject?>,
      value: DependencyAnalyzerProject?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      return JLabel()
        .apply { if (value != null) icon = iconProvider.projectIcon }
        .apply { if (value != null) text = value.title }
        .apply { border = emptyListCellBorder(list, index) }
        .apply { iconTextGap = JBUI.scale(ICON_TEXT_GAP) }
        .apply { background = if (isSelected) list.selectionBackground else list.background }
        .apply { foreground = if (isSelected) list.selectionForeground else list.foreground }
        .apply { isOpaque = true }
        .apply { isEnabled = list.isEnabled }
        .apply { font = list.font }
    }
  }

  private inner class ExternalProjectDropDownLink(
    property: ObservableMutableProperty<DependencyAnalyzerProject?>,
    externalProjects: List<DependencyAnalyzerProject>,
  ) : DropDownLink<DependencyAnalyzerProject?>(
    property.get(),
    { createPopup(externalProjects, it::selectedItem.setter) }
  ) {
    override fun popupPoint() =
      super.popupPoint()
        .apply { x += insets.left }
        .apply { x -= JBUI.scale(BORDER) }
        .apply { x -= iconProvider.projectIcon.iconWidth }
        .apply { x -= JBUI.scale(ICON_TEXT_GAP) }

    override fun itemToString(item: DependencyAnalyzerProject?): String = when (item) {
      null -> ExternalSystemBundle.message("external.system.dependency.analyzer.projects.empty")
      else -> item.title
    }

    init {
      autoHideOnDisable = false
      foreground = JBUI.CurrentTheme.Label.foreground()
      whenItemSelected { text = itemToString(selectedItem) }
      bind(property)
    }
  }
}



