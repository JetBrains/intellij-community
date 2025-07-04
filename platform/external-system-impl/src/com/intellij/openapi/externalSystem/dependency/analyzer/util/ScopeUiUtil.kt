// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.observable.util.whenMousePressed
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ListUtil
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.*


internal class SearchScopeSelector(property: ObservableMutableProperty<List<ScopeItem>>) : JPanel() {
  init {
    val dropDownLink = SearchScopeDropDownLink(property)
      .apply { border = JBUI.Borders.empty(BORDER, ICON_TEXT_GAP / 2, BORDER, BORDER) }
    val label = JLabel(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.label"))
      .apply { border = JBUI.Borders.empty(BORDER, BORDER, BORDER, ICON_TEXT_GAP / 2) }
      .apply { labelFor = dropDownLink }

    layout = ListLayout.horizontal(0)
    border = JBUI.Borders.empty()
    add(label)
    add(dropDownLink)
  }
}

private class SearchScopePopupContent(scopes: List<ScopeItem>) : JBList<SearchScopeItem>() {

  private val anyGroup: SearchScopeItem.Group
  private val anyScopes: List<SearchScopeItem.Element>

  init {
    val propertyGraph = PropertyGraph(isBlockPropagation = false)

    anyScopes = scopes.map { scope ->
      SearchScopeItem.Element(
        scope.scope,
        propertyGraph.property(scope.isSelected)
      )
    }
    anyGroup = SearchScopeItem.Group(
      ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any"),
      propertyGraph.lazyProperty { suggestGroupState(anyScopes) }
    )

    propertyGraph.afterPropagation {
      repaint()
    }
    initProperties(anyGroup, anyScopes)
  }

  init {
    model = createDefaultListModel(getItems(anyGroup, anyScopes))
    border = emptyListBorder()
    cellRenderer = SearchScopeRenderer()
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    ListUtil.installAutoSelectOnMouseMove(this)
    whenMousePressed {
      when (val scope = selectedValue) {
        is SearchScopeItem.Group -> scope.property.set(ThreeStateCheckBox.nextState(scope.property.get(), false))
        is SearchScopeItem.Element -> scope.property.set(!scope.property.get())
      }
    }
  }

  fun afterChange(listener: (List<ScopeItem>) -> Unit) {
    for (scope in anyScopes) {
      scope.property.afterChange {
        listener(anyScopes.map { ScopeItem(it.scope, it.property.get()) })
      }
    }
  }

  companion object {

    fun createPopup(scopes: List<ScopeItem>, onChange: (List<ScopeItem>) -> Unit): JBPopup {
      val content = SearchScopePopupContent(scopes)
      content.afterChange(onChange)
      return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .createPopup()
    }

    private fun suggestGroupState(scopes: List<SearchScopeItem.Element>): ThreeStateCheckBox.State {
      return when {
        scopes.all { it.property.get() } -> ThreeStateCheckBox.State.SELECTED
        !scopes.any { it.property.get() } -> ThreeStateCheckBox.State.NOT_SELECTED
        else -> ThreeStateCheckBox.State.DONT_CARE
      }
    }

    private fun suggestScopeState(group: SearchScopeItem.Group, scope: SearchScopeItem.Element): Boolean {
      return when (group.property.get()) {
        ThreeStateCheckBox.State.SELECTED -> true
        ThreeStateCheckBox.State.NOT_SELECTED -> false
        ThreeStateCheckBox.State.DONT_CARE -> scope.property.get()
      }
    }

    private fun initProperties(group: SearchScopeItem.Group, scopes: List<SearchScopeItem.Element>) {
      for (scope in scopes) {
        group.property.dependsOn(scope.property) {
          suggestGroupState(scopes)
        }
        scope.property.dependsOn(group.property) {
          suggestScopeState(group, scope)
        }
      }
    }

    private fun getItems(group: SearchScopeItem.Group, scopes: List<SearchScopeItem.Element>): List<SearchScopeItem> {
      if (scopes.isEmpty()) {
        return emptyList()
      }
      return ContainerUtil.concat(listOf(group), scopes)
    }
  }
}

private class SearchScopeRenderer : ListCellRenderer<SearchScopeItem> {

  override fun getListCellRendererComponent(
    list: JList<out SearchScopeItem>,
    value: SearchScopeItem,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val checkBox = when (value) {
      is SearchScopeItem.Group ->
        ThreeStateCheckBox(value.title)
          .apply { isThirdStateEnabled = false }
          .bind(value.property)
      is SearchScopeItem.Element ->
        JCheckBox(value.title)
          .bind(value.property)
    }
    val indent = when (value) {
      is SearchScopeItem.Group -> 0
      is SearchScopeItem.Element -> 1
    }
    return checkBox
      .apply { border = emptyListCellBorder(list, index, indent) }
      .apply { background = if (isSelected) list.selectionBackground else list.background }
      .apply { foreground = if (isSelected) list.selectionForeground else list.foreground }
      .apply { isOpaque = true }
      .apply { isEnabled = list.isEnabled }
      .apply { font = list.font }
  }
}

private class SearchScopeDropDownLink(
  property: ObservableMutableProperty<List<ScopeItem>>
) : DropDownLink<List<ScopeItem>>(
  property.get(),
  { SearchScopePopupContent.createPopup(property.get(), it::selectedItem.setter) }
) {
  override fun popupPoint() =
    super.popupPoint()
      .apply { x += insets.left }

  override fun itemToString(item: List<ScopeItem>): @NlsSafe String {
    return when {
      item.all { it.isSelected } -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any")
      !item.any { it.isSelected } -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.none")
      else -> {
        val scopes = item.filter { it.isSelected }.map { it.scope.title }
        StringUtil.shortenPathWithEllipsis(NlsMessages.formatNarrowAndList(scopes), 30, true)
      }
    }
  }

  init {
    autoHideOnDisable = false
    foreground = JBUI.CurrentTheme.Label.foreground()
    whenItemSelected { text = itemToString(selectedItem) }
    bind(property)
  }
}

internal class ScopeItem(
  val scope: Scope,
  val isSelected: Boolean
) {
  override fun toString() = "$isSelected: $scope"
}

private sealed interface SearchScopeItem {

  val title: @Nls(capitalization = Nls.Capitalization.Title) String

  class Group(
    override val title: @Nls(capitalization = Nls.Capitalization.Title) String,
    val property: GraphProperty<ThreeStateCheckBox.State>,
  ) : SearchScopeItem

  class Element(
    val scope: Scope,
    val property: GraphProperty<Boolean>,
  ) : SearchScopeItem {
    override val title: @Nls(capitalization = Nls.Capitalization.Title) String by scope::title
  }
}