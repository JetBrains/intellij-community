// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.InlineIconButton
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAndAwaitListSubmission
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.*
import com.intellij.ui.components.GradientViewport
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.geom.Ellipse2D
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

abstract class ReviewListSearchPanelFactory<S : ReviewListSearchValue, VM : ReviewListSearchPanelViewModel<S>>(
  protected val vm: VM
) {

  fun create(viewScope: CoroutineScope, quickFilters: List<Pair<@Nls String, S>>): JComponent {
    val searchField = ReviewListSearchTextFieldFactory(vm.queryState).create(viewScope, chooseFromHistory = { point ->
      val value = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(vm.getSearchHistory().reversed())
        .setRenderer(SimpleListCellRenderer.create { label, value, _ ->
          label.text = getShortText(value)
        })
        .createPopup()
        .showAndAwaitListSubmission<S>(point)
      if (value != null) {
        vm.searchState.update { value }
      }
    })

    val filters = createFilters(viewScope)

    val filtersPanel = JPanel(HorizontalLayout(4)).apply {
      isOpaque = false
      filters.forEach { add(it, HorizontalLayout.LEFT) }
    }.let {
      ScrollPaneFactory.createScrollPane(it, true).apply {
        viewport = GradientViewport(it, JBUI.insets(0, 10), false)

        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        horizontalScrollBar = JBThinOverlappingScrollBar(Adjustable.HORIZONTAL)
      }
    }

    val quickFilterButton = QuickFilterButtonFactory().create(viewScope, quickFilters)

    val filterPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyTop(10)
      isOpaque = false
      add(quickFilterButton, BorderLayout.WEST)
      add(filtersPanel, BorderLayout.CENTER)
    }

    val searchPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.BOTTOM), JBUI.Borders.empty(8, 10, 0, 10))
      add(searchField, BorderLayout.CENTER)
      add(filterPanel, BorderLayout.SOUTH)
    }

    return searchPanel
  }

  protected abstract fun getShortText(searchValue: S): @Nls String

  protected abstract fun createFilters(viewScope: CoroutineScope): List<JComponent>

  private inner class QuickFilterButtonFactory {

    fun create(viewScope: CoroutineScope, quickFilters: List<Pair<String, S>>): JComponent {
      val button = InlineIconButton(AllIcons.General.Filter).apply {
        border = JBUI.Borders.empty(3)
      }.also {
        it.actionListener = ActionListener { _ ->
          showQuickFiltersPopup(it, quickFilters)
        }
      }

      viewScope.launch {
        vm.searchState.collect {
          button.icon = if (it.filterCount == 0) AllIcons.General.Filter else IconWithNotifyDot(AllIcons.General.Filter)
        }
      }

      return button
    }

    private fun showQuickFiltersPopup(parentComponent: JComponent, quickFilters: List<Pair<@Nls String, S>>) {
      val quickFiltersActions =
        quickFilters.map { (name, search) -> QuickFilterAction(name, search) } +
        Separator() +
        ClearFiltersAction()


      JBPopupFactory.getInstance()
        .createActionGroupPopup(CollaborationToolsBundle.message("review.list.filter.quick.title"), DefaultActionGroup(quickFiltersActions),
                                DataManager.getInstance().getDataContext(parentComponent),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                false)
        .showUnderneathOf(parentComponent)
    }

    private inner class QuickFilterAction(name: @Nls String, private val search: S)
      : DumbAwareAction(name), Toggleable {
      override fun update(e: AnActionEvent) = Toggleable.setSelected(e.presentation, vm.searchState.value == search)
      override fun actionPerformed(e: AnActionEvent) = vm.searchState.update { search }
    }

    private inner class ClearFiltersAction
      : DumbAwareAction(CollaborationToolsBundle.message("review.list.filter.quick.clear", vm.searchState.value.filterCount)) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = vm.searchState.value.filterCount > 0
      }

      override fun actionPerformed(e: AnActionEvent) = vm.searchState.update { vm.emptySearch }
    }

    //TODO: request a ready-made icon from UI and also a proper icon for old UI
    private inner class IconWithNotifyDot(private val originalIcon: Icon) : Icon by originalIcon {
      override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        originalIcon.paintIcon(c, g, x, y)
        g as Graphics2D
        val dotSize = JBUIScale.scale(6)
        val notifyDotShape = Ellipse2D.Float((iconWidth - dotSize).toFloat(), 0f, dotSize.toFloat(), dotSize.toFloat())
        g.color = ColorUtil.fromHex("#3574F0")
        g.fill(notifyDotShape)
      }
    }
  }
}