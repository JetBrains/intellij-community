// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.ui.BadgeIconSupplier
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal object QuickFilterButtonFactory {
  private val FILTER_ICON: BadgeIconSupplier = BadgeIconSupplier(AllIcons.General.Filter)

  fun <S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>> create(
    searchState: StateFlow<S>,
    quickFilters: List<Q>,
    filterListener: ((Q) -> Unit)?,
    quickFilterTitleProvider: (Q) -> @Nls String,
    setSearchQuery: (S) -> Unit,
    clearSearchQuery: () -> Unit,
  ): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(
      "Review.FilterToolbar",
      DefaultActionGroup(FilterPopupMenuAction(
        searchState,
        quickFilters,
        filterListener,
        quickFilterTitleProvider,
        setSearchQuery,
        clearSearchQuery
      )),
      true
    ).apply {
      layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
      component.isOpaque = false
      component.border = JBUI.Borders.empty()
      targetComponent = null
    }

    toolbar.component.launchOnShow("ReviewFilterToolbarListener") {
      Disposer.newDisposable().use { disposable ->
        toolbar.addListener(object : ActionToolbarListener {
          override fun actionsUpdated() = UIUtil.forEachComponentInHierarchy(toolbar.component) {
            if (it is ActionButton) {
              it.setFocusable(true)
            }
          }
        }, disposable)
        awaitCancellation()
      }
    }

    toolbar.component.launchOnShow("ReviewFilterToolbar") {
      searchState.collect {
        toolbar.updateActionsAsync()
      }
    }

    return toolbar.component
  }

  private fun <S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>> showQuickFiltersPopup(
    parentComponent: JComponent,
    searchState: StateFlow<S>,
    quickFilters: List<Q>,
    filterListener: ((Q) -> Unit)?,
    quickFilterTitleProvider: (Q) -> @Nls String,
    setSearchQuery: (S) -> Unit,
    clearSearchQuery: () -> Unit,
  ) {
    val quickFiltersActions = buildList {
      add(Separator(CollaborationToolsBundle.message("review.list.filter.quick.title")))
      quickFilters.forEach { filter ->
        add(QuickFilterAction(searchState, quickFilterTitleProvider(filter), filter, filterListener, setSearchQuery))
      }
      add(Separator())
      add(ClearFiltersAction(searchState, clearSearchQuery))
    }

    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, DefaultActionGroup(quickFiltersActions),
                              DataManager.getInstance().getDataContext(parentComponent),
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false)
      .showUnderneathOf(parentComponent)
  }

  private class FilterPopupMenuAction<S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>>(
    private val searchState: StateFlow<S>,
    private val quickFilters: List<Q>,
    private val filterListener: ((Q) -> Unit)?,
    private val quickFilterTitleProvider: (Q) -> @Nls String,
    private val setSearchQuery: (S) -> Unit,
    private val clearSearchQuery: () -> Unit,
  ) : DumbAwareAction(CollaborationToolsBundle.message("review.list.filter.quick.title")), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.icon = FILTER_ICON.getLiveIndicatorIcon(searchState.value.filterCount != 0)
    }

    override fun actionPerformed(e: AnActionEvent) {
      showQuickFiltersPopup(
        e.inputEvent!!.component as JComponent,
        searchState,
        quickFilters,
        filterListener,
        quickFilterTitleProvider,
        setSearchQuery,
        clearSearchQuery
      )
    }
  }

  private class QuickFilterAction<S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>>(
    private val searchState: StateFlow<S>,
    name: @Nls String,
    private val filter: Q,
    private val filterListener: ((Q) -> Unit)?,
    private val setSearchQuery: (S) -> Unit,
  ) : DumbAwareAction(name), Toggleable {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) = Toggleable.setSelected(e.presentation, searchState.value == filter.filter)
    override fun actionPerformed(e: AnActionEvent) {
      filterListener?.invoke(filter)
      setSearchQuery(filter.filter)
    }
  }

  private class ClearFiltersAction<S : ReviewListSearchValue>(
    private val searchState: StateFlow<S>,
    private val clearSearchQuery: () -> Unit,
  ) : DumbAwareAction(CollaborationToolsBundle.message("review.list.filter.quick.clear", searchState.value.filterCount)) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = searchState.value.filterCount > 0
    }

    override fun actionPerformed(e: AnActionEvent) = clearSearchQuery()
  }
}