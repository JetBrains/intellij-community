// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.util.popup.showAndAwaitListSubmission
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.GradientViewport
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Adjustable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

abstract class ReviewListSearchPanelFactory<S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>, VM : ReviewListSearchPanelViewModel<S, Q>>(
  protected val vm: VM
) {

  fun create(viewScope: CoroutineScope): JComponent =
    create(viewScope, null)

  @Internal
  fun create(viewScope: CoroutineScope, quickFilterListener: ((Q) -> Unit)?): JComponent {
    val searchField = ReviewListSearchTextFieldFactory(vm.queryState).create(viewScope, chooseFromHistory = { point ->
      val value = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(vm.getSearchHistory().reversed())
        .setRenderer(SimpleListCellRenderer.create { label, value, _ ->
          label.text = getShortText(value)
        })
        .createPopup()
        .showAndAwaitListSubmission<S>(point, ShowDirection.BELOW)
      if (value != null) {
        vm.searchState.update { value }
      }
    })

    val filters = createFilters(viewScope)

    val filtersPanel = HorizontalListPanel(4).apply {
      filters.forEach { add(it) }
    }.let {
      ScrollPaneFactory.createScrollPane(it, true).apply {
        viewport = GradientViewport(it, JBUI.insets(0, 10), false)

        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        horizontalScrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)

        ClientProperty.put(this, JBScrollPane.FORCE_HORIZONTAL_SCROLL, true)
      }
    }

    val quickFilterButton = QuickFilterButtonFactory().create(viewScope, vm.quickFilters, quickFilterListener)

    val filterPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyTop(10)
      isOpaque = false
      add(quickFilterButton, BorderLayout.WEST)
      add(filtersPanel, BorderLayout.CENTER)
    }

    val searchPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(8, 10, 0, 10)
      add(searchField, BorderLayout.CENTER)
      add(filterPanel, BorderLayout.SOUTH)
    }

    return searchPanel
  }

  protected abstract fun getShortText(searchValue: S): @Nls String

  protected abstract fun createFilters(viewScope: CoroutineScope): List<JComponent>

  protected abstract fun Q.getQuickFilterTitle(): @Nls String

  private inner class QuickFilterButtonFactory {

    fun create(viewScope: CoroutineScope, quickFilters: List<Q>, filterListener: ((Q) -> Unit)?): JComponent {
      val toolbar = ActionManager.getInstance().createActionToolbar(
        "Review.FilterToolbar",
        DefaultActionGroup(FilterPopupMenuAction(quickFilters, filterListener)),
        true
      ).apply {
        layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
        component.isOpaque = false
        component.border = null
        targetComponent = null

        addListener(object : ActionToolbarListener {
          override fun actionsUpdated() = UIUtil.forEachComponentInHierarchy(component) {
            if (it is ActionButton) {
              it.setFocusable(true)
            }
          }
        }, viewScope.nestedDisposable())
      }

      viewScope.launch {
        vm.searchState.collect {
          toolbar.updateActionsAsync()
        }
      }
      return toolbar.component
    }

    private fun showQuickFiltersPopup(parentComponent: JComponent, quickFilters: List<Q>, filterListener: ((Q) -> Unit)?) {
      val quickFiltersActions = buildList {
        add(Separator(CollaborationToolsBundle.message("review.list.filter.quick.title")))
        quickFilters.forEach {
          add(QuickFilterAction(it.getQuickFilterTitle(), it, filterListener))
        }
        add(Separator())
        add(ClearFiltersAction())
      }

      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, DefaultActionGroup(quickFiltersActions),
                                DataManager.getInstance().getDataContext(parentComponent),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                false)
        .showUnderneathOf(parentComponent)
    }

    private inner class FilterPopupMenuAction(private val quickFilters: List<Q>, private val filterListener: ((Q) -> Unit)?)
      : DumbAwareAction(CollaborationToolsBundle.message("review.list.filter.quick.title")),
        DumbAware {
      override fun getActionUpdateThread() = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        e.presentation.icon = FILTER_ICON.getLiveIndicatorIcon(vm.searchState.value.filterCount != 0)
      }

      override fun actionPerformed(e: AnActionEvent) {
        showQuickFiltersPopup(e.inputEvent!!.component as JComponent, quickFilters, filterListener)
      }
    }

    private inner class QuickFilterAction(name: @Nls String, private val filter: Q, private val filterListener: ((Q) -> Unit)?)
      : DumbAwareAction(name), Toggleable {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
      override fun update(e: AnActionEvent) = Toggleable.setSelected(e.presentation, vm.searchState.value == filter.filter)
      override fun actionPerformed(e: AnActionEvent) {
        filterListener?.invoke(filter)
        vm.searchState.update { filter.filter }
      }
    }

    private inner class ClearFiltersAction
      : DumbAwareAction(CollaborationToolsBundle.message("review.list.filter.quick.clear", vm.searchState.value.filterCount)) {

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = vm.searchState.value.filterCount > 0
      }

      override fun actionPerformed(e: AnActionEvent) = vm.searchState.update { vm.emptySearch }
    }
  }

  companion object {
    private val FILTER_ICON: BadgeIconSupplier = BadgeIconSupplier(AllIcons.General.Filter)
  }
}