// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer.util

import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.observable.util.whenMousePressed
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ListUtil
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import org.apache.commons.lang.StringUtils
import java.awt.Component
import javax.swing.*


internal class SearchScopeSelector(property: ObservableMutableProperty<List<ScopeItem>>) : JPanel() {
  init {
    val dropDownLink = SearchScopeDropDownLink(property)
      .apply { border = JBUI.Borders.empty(BORDER, ICON_TEXT_GAP / 2, BORDER, BORDER) }
    val label = JLabel(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.label"))
      .apply { border = JBUI.Borders.empty(BORDER, BORDER, BORDER, ICON_TEXT_GAP / 2) }
      .apply { labelFor = dropDownLink }

    layout = HorizontalLayout(0)
    border = JBUI.Borders.empty()
    add(label)
    add(dropDownLink)
  }
}

private class SearchScopePopupContent(scopes: List<ScopeItem>) : JBList<ScopeProperty>() {

  private val propertyGraph = PropertyGraph(isBlockPropagation = false)
  private val anyScopeProperty = propertyGraph.graphProperty(::suggestAnyScopeState)
  private val scopeProperties = scopes.map { ScopeProperty.Just(it.scope, propertyGraph.graphProperty { it.isSelected }) }

  private fun suggestAnyScopeState(): ThreeStateCheckBox.State {
    return when {
      scopeProperties.all { it.property.get() } -> ThreeStateCheckBox.State.SELECTED
      !scopeProperties.any { it.property.get() } -> ThreeStateCheckBox.State.NOT_SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  private fun suggestScopeState(currentState: Boolean): Boolean {
    return when (anyScopeProperty.get()) {
      ThreeStateCheckBox.State.SELECTED -> true
      ThreeStateCheckBox.State.NOT_SELECTED -> false
      ThreeStateCheckBox.State.DONT_CARE -> currentState
    }
  }

  fun afterChange(listener: (List<ScopeItem>) -> Unit) {
    for (scope in scopeProperties) {
      scope.property.afterChange {
        listener(scopeProperties.map { ScopeItem(it.scope, it.property.get()) })
      }
    }
  }

  init {
    val anyScope = ScopeProperty.Any(anyScopeProperty)
    model = createDefaultListModel(listOf(anyScope) + scopeProperties)
    border = emptyListBorder()
    cellRenderer = SearchScopePropertyRenderer()
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    ListUtil.installAutoSelectOnMouseMove(this)
    setupListPopupPreferredWidth(this)
    whenMousePressed {
      when (val scope = selectedValue) {
        is ScopeProperty.Any -> scope.property.set(ThreeStateCheckBox.nextState(scope.property.get(), false))
        is ScopeProperty.Just -> scope.property.set(!scope.property.get())
      }
    }

    propertyGraph.afterPropagation {
      repaint()
    }
    for (scope in scopeProperties) {
      anyScopeProperty.dependsOn(scope.property) {
        suggestAnyScopeState()
      }
      scope.property.dependsOn(anyScopeProperty) {
        suggestScopeState(scope.property.get())
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
  }
}

private class SearchScopePropertyRenderer : ListCellRenderer<ScopeProperty> {
  override fun getListCellRendererComponent(
    list: JList<out ScopeProperty>,
    value: ScopeProperty,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val checkBox = when (value) {
      is ScopeProperty.Any ->
        ThreeStateCheckBox(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.any"))
          .apply { isThirdStateEnabled = false }
          .apply { state = value.property.get() }
          .bind(value.property)
      is ScopeProperty.Just ->
        JCheckBox(value.scope.title)
          .apply { this@apply.isSelected = value.property.get() }
          .bind(value.property)
    }
    return checkBox
      .apply { border = emptyListCellBorder(list, index, if (index > 0) 1 else 0) }
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
        StringUtils.abbreviate(NlsMessages.formatNarrowAndList(scopes), 30)
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

private sealed interface ScopeProperty {
  class Any(val property: GraphProperty<ThreeStateCheckBox.State>) : ScopeProperty
  class Just(val scope: Scope, val property: GraphProperty<Boolean>) : ScopeProperty
}