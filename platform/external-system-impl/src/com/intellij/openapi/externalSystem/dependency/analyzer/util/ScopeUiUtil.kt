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
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.speedSearch.ListWithFilter
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

private class SearchScopePopupContent(scopes: List<ScopeItem>) {

  val component: JComponent

  private val allScopes: List<SearchScopeItem.Element>

  private val standardGroup: SearchScopeItem.Group
  private val standardScopes: List<SearchScopeItem.Element>

  private val customGroup: SearchScopeItem.Group
  private val customScopes: List<SearchScopeItem.Element>

  init {
    val propertyGraph = PropertyGraph(isBlockPropagation = false)

    allScopes = scopes
      .sortedWith(Comparator.comparing({ it.scope.title }, NaturalComparator.INSTANCE))
      .map { scope ->
        SearchScopeItem.Element(
          scope.scope,
          propertyGraph.property(scope.isSelected)
        )
      }
    standardScopes = allScopes.filter { it.scope.type == Scope.Type.STANDARD }
    customScopes = allScopes.filter { it.scope.type == Scope.Type.CUSTOM }

    standardGroup = SearchScopeItem.Group(
      when (standardScopes.size == allScopes.size) {
        true -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any")
        else -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.standard")
      },
      propertyGraph.lazyProperty { suggestGroupState(standardScopes) }
    )
    customGroup = SearchScopeItem.Group(
      when (customScopes.size == allScopes.size) {
        true -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any")
        else -> ExternalSystemBundle.message("external.system.dependency.analyzer.scope.custom")
      },
      propertyGraph.lazyProperty { suggestGroupState(customScopes) }
    )

    propertyGraph.afterPropagation {
      component.repaint()
    }
    initProperties(standardGroup, standardScopes)
    initProperties(customGroup, customScopes)
  }

  init {
    val items = ContainerUtil.concat(
      getItems(standardGroup, standardScopes),
      getItems(customGroup, customScopes)
    )
    val list = JBList(items).apply {
      border = emptyListBorder()
      cellRenderer = SearchScopeRenderer()
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      ListUtil.installAutoSelectOnMouseMove(this)
    }
    list.whenMousePressed {
      when (val scope = list.selectedValue) {
        is SearchScopeItem.Group -> scope.property.set(ThreeStateCheckBox.nextState(scope.property.get(), false))
        is SearchScopeItem.Element -> scope.property.set(!scope.property.get())
      }
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

  fun afterChange(listener: (List<ScopeItem>) -> Unit) {
    for (scope in allScopes) {
      scope.property.afterChange {
        listener(allScopes.map { ScopeItem(it.scope, it.property.get()) })
      }
    }
  }

  companion object {

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
  { createPopup(property.get(), it::selectedItem.setter) }
) {

  override fun popupPoint() =
    super.popupPoint()
      .apply { x += insets.left }

  override fun itemToString(item: List<ScopeItem>): @NlsSafe String {
    val selectedScopes = item.filter { it.isSelected }
    val standardScopes = item.filter { it.scope.type == Scope.Type.STANDARD }
    val selectedStandardScopes = standardScopes.filter { it.isSelected }
    val customScopes = item.filter { it.scope.type == Scope.Type.CUSTOM }
    val selectedCustomScopes = customScopes.filter { it.isSelected }
    return when {
      selectedScopes.isEmpty() ->
        ExternalSystemBundle.message("external.system.dependency.analyzer.scope.none")
      selectedScopes.size == item.size ->
        ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any")
      selectedScopes.size == standardScopes.size &&
      selectedScopes.size == selectedStandardScopes.size ->
        ExternalSystemBundle.message("external.system.dependency.analyzer.scope.standard")
      selectedScopes.size == customScopes.size &&
      selectedScopes.size == selectedCustomScopes.size ->
        ExternalSystemBundle.message("external.system.dependency.analyzer.scope.custom")
      else ->
        StringUtil.shortenPathWithEllipsis(NlsMessages.formatNarrowAndList(selectedScopes.map { it.scope.title }), 30, true)
    }
  }

  init {
    autoHideOnDisable = false
    foreground = JBUI.CurrentTheme.Label.foreground()
    whenItemSelected { text = itemToString(selectedItem) }
    bind(property)
  }

  companion object {

    fun createPopup(scopes: List<ScopeItem>, onChange: (List<ScopeItem>) -> Unit): JBPopup {
      val content = SearchScopePopupContent(scopes)
      content.afterChange(onChange)
      return JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content.component, null)
        .setResizable(true)
        .setRequestFocus(true)
        .createPopup()
    }
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