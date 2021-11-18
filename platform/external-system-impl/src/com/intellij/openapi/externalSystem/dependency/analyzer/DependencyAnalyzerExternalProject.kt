// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.ObservableClearableProperty
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.whenItemSelected
import com.intellij.openapi.ui.whenMousePressed
import com.intellij.ui.ListUtil
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.*

internal class ExternalProjectSelector(
  property: ObservableClearableProperty<String?>,
  externalProjects: List<String>,
  private val dependencyContributor: DependencyContributor,
  private val iconProvider: ExternalSystemIconProvider
) : JPanel() {
  init {
    val dropDownLink = ExternalProjectDropDownLink(property, externalProjects)
      .apply { border = JBUI.Borders.empty(BORDER, ICON_TEXT_GAP / 2, BORDER, BORDER) }
    val label = JLabel(iconProvider.projectIcon)
      .apply { border = JBUI.Borders.empty(BORDER, BORDER, BORDER, ICON_TEXT_GAP / 2) }
      .apply { labelFor = dropDownLink }

    layout = HorizontalLayout(0)
    border = JBUI.Borders.empty()
    add(label)
    add(dropDownLink)
  }

  private fun createPopup(externalProject: List<String>, onChange: (String) -> Unit): JBPopup {
    val content = ExternalProjectPopupContent(externalProject)
      .apply { whenMousePressed { onChange(selectedValue) } }
    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content, null)
      .createPopup()
      .apply { content.whenMousePressed(::closeOk) }
  }

  private inner class ExternalProjectPopupContent(externalProject: List<String>) : JBList<String>() {
    init {
      model = createDefaultListModel(externalProject)
      border = emptyListBorder()
      cellRenderer = ExternalProjectRenderer()
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      ListUtil.installAutoSelectOnMouseMove(this)
      setupListPopupPreferredWidth(this)
    }
  }

  private inner class ExternalProjectRenderer : ListCellRenderer<String?> {
    override fun getListCellRendererComponent(
      list: JList<out String?>,
      value: String?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      return JLabel()
        .apply { if (value != null) icon = iconProvider.projectIcon }
        .apply { if (value != null) text = dependencyContributor.getExternalProjectName(value) }
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
    property: ObservableClearableProperty<String?>,
    externalProjects: List<String>,
  ) : DropDownLink<String?>(
    property.get(),
    { createPopup(externalProjects, it::selectedItem.setter) }
  ) {

    override fun popupPoint() =
      super.popupPoint()
        .apply { x += insets.left }
        .apply { x -= JBUI.scale(BORDER) }
        .apply { x -= iconProvider.projectIcon.iconWidth }
        .apply { x -= JBUI.scale(ICON_TEXT_GAP) }

    override fun itemToString(item: String?): String = when (item) {
      null -> ExternalSystemBundle.message("external.system.dependency.analyzer.projects.empty")
      else -> dependencyContributor.getExternalProjectName(item)
    }

    init {
      foreground = JBUI.CurrentTheme.Label.foreground()
      whenItemSelected { text = itemToString(selectedItem) }
      bind(property)
    }
  }
}



