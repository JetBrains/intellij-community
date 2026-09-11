// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CheckedActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.FieldInplaceActionButtonLook
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class UnifiedPluginsSearchToolbar(
  private val onIntent: (UnifiedPluginSearchControlIntent) -> Unit,
) {
  private var state = UnifiedPluginsSearchControlsState()
  private var rendered = false

  private val sortActionGroup = SortActionGroup()
  private val filterActionGroup = FilterActionGroup()
  private val sortButton = createButton(sortActionGroup)
  private val filterButton = createButton(filterActionGroup)
  private val sortFilterGap = Box.createHorizontalStrut(JBUI.scale(SEARCH_ACTIONS_GAP)).apply {
    isVisible = false
  }

  val component: JComponent = JPanel(HorizontalLayout(0, SwingConstants.CENTER)).apply {
    isOpaque = false
    add(sortButton)
    add(sortFilterGap)
    add(filterButton)
    add(Box.createHorizontalStrut(JBUI.scale(SEARCH_ACTIONS_RIGHT_INSET)))
  }

  @Suppress("DialogTitleCapitalization")
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun render(state: UnifiedPluginsSearchControlsState) {
    if (rendered && this.state == state) return
    if (this.state.sortVisible && !state.sortVisible && sortButton.isFocusOwner) {
      IdeFocusManager.getGlobalInstance().requestFocus(filterButton, false)
    }
    this.state = state
    rendered = true
    sortFilterGap.isVisible = state.sortVisible
    sortButton.apply {
      isVisible = state.sortVisible
      presentation.apply {
        icon = AllIcons.General.SortBy
        text = IdeBundle.message(
          "plugins.configurable.sort.marketplace.results",
          state.effectiveSort.presentableNameSupplier.get(),
        )
      }
    }
    filterButton.presentation.apply {
      icon = FILTER_ICON.getLiveIndicatorIcon(state.filterSelected)
      text = IdeBundle.message("plugins.configurable.filter.plugins")
      Toggleable.setSelected(this, state.filterSelected)
    }
    component.revalidate()
    component.repaint()
  }

  private fun createButton(action: ActionGroup): ActionButton {
    return ActionButton(action, null, SEARCH_TOOLBAR_PLACE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
      isFocusable = true
      setLook(FieldInplaceActionButtonLook())
      presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
    }
  }

  private inner class SortActionGroup : ActionGroup(null, true), CheckedActionGroup {
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
      return createUnifiedPluginSortActionGroup(state, onIntent).getChildren(event)
    }
  }

  private inner class FilterActionGroup : ActionGroup(null, true), CheckedActionGroup {
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
      return createUnifiedPluginFilterActionGroup(state, onIntent).getChildren(event)
    }
  }

  private companion object {
    const val SEARCH_ACTIONS_GAP: Int = 4
    const val SEARCH_ACTIONS_RIGHT_INSET: Int = 3
    const val SEARCH_TOOLBAR_PLACE: String = "UnifiedPlugins.SearchToolbar"
    val FILTER_ICON = BadgeIconSupplier(AllIcons.General.Filter)
  }
}
