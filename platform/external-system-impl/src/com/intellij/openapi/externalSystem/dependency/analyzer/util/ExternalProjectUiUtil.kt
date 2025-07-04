// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.observable.util.whenMousePressed
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.*

internal class ExternalProjectSelector(
  property: ObservableMutableProperty<DependencyAnalyzerProject?>,
  externalProjects: List<DependencyAnalyzerProject>,
  iconProvider: ExternalSystemIconProvider,
) : JPanel() {

  init {
    val dropDownLink = ExternalProjectDropDownLink(property, externalProjects, iconProvider)
      .apply { border = JBUI.Borders.empty(BORDER, ICON_TEXT_GAP / 2, BORDER, BORDER) }
    val label = JLabel(iconProvider.projectIcon)
      .apply { border = JBUI.Borders.empty(BORDER, BORDER, BORDER, ICON_TEXT_GAP / 2) }
      .apply { labelFor = dropDownLink }

    layout = ListLayout.horizontal(0)
    border = JBUI.Borders.empty()
    add(label)
    add(dropDownLink)
  }
}

private class ExternalProjectPopupContent(
  externalProjects: List<DependencyAnalyzerProject>,
  iconProvider: ExternalSystemIconProvider,
) {

  val component: JComponent

  val list: JBList<DependencyAnalyzerProject>

  init {
    val elements = externalProjects.sortedWith(Comparator.comparing({ it.title }, NaturalComparator.INSTANCE))
    list = JBList(elements).apply {
      border = emptyListBorder()
      cellRenderer = ExternalProjectRenderer(iconProvider)
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      ListUtil.installAutoSelectOnMouseMove(this)
    }
    component = ListWithFilter.wrap(
      /* list = */ list,
      /* scrollPane = */ ScrollPaneFactory.createScrollPane(list),
      /* namer = */ { it.title },
      /* highlightAllOccurrences = */ false,
      /* searchFieldAlwaysVisible = */ false,
      /* searchFieldWithoutBorder = */ true
    )
  }

  fun afterChange(listener: (DependencyAnalyzerProject) -> Unit) {
    list.whenMousePressed {
      listener(list.selectedValue)
    }
  }
}

private class ExternalProjectRenderer(
  private val iconProvider: ExternalSystemIconProvider,
) : ListCellRenderer<DependencyAnalyzerProject?> {

  override fun getListCellRendererComponent(
    list: JList<out DependencyAnalyzerProject?>,
    value: DependencyAnalyzerProject?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
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

private class ExternalProjectDropDownLink(
  property: ObservableMutableProperty<DependencyAnalyzerProject?>,
  externalProjects: List<DependencyAnalyzerProject>,
  private val iconProvider: ExternalSystemIconProvider,
) : DropDownLink<DependencyAnalyzerProject?>(
  property.get(),
  { createPopup(externalProjects, iconProvider, it::selectedItem.setter) }
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

  companion object {

    fun createPopup(
      externalProjects: List<DependencyAnalyzerProject>,
      iconProvider: ExternalSystemIconProvider,
      onChange: (DependencyAnalyzerProject) -> Unit
    ): JBPopup {
      val content = ExternalProjectPopupContent(externalProjects, iconProvider)
      content.afterChange(onChange)
      return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content.component, null)
        .setResizable(true)
        .setRequestFocus(true)
        .createPopup()
        .apply { content.list.whenMousePressed(listener = ::closeOk) }
    }
  }
}



