// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindTooltipTextIn
import com.intellij.collaboration.ui.util.popup.showAndAwait
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

object CodeReviewDetailsBranchComponentFactory {
  /**
   * Creates the label with the review target and source branches.
   */
  fun create(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent =
    object : JBLabel() {
      // the width shrinks to cut a long branch name, the height does not shrink
      override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)
    }.apply {
      setCopyable(true)
      foreground = NamedColorUtil.getInactiveTextColor()
      val branchesText = combineState(scope, branchesVm.targetBranch, branchesVm.sourceBranch) { target, source ->
        CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source)
      }
      // the copyable label renders its text as HTML, and a branch name can contain '<' or '&'
      bindTextIn(scope, branchesText.map(StringUtil::escapeXmlEntities))
      bindTooltipTextIn(scope, branchesText)
    }

  fun createActionLinks(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
    timelineLink: JComponent,
  ): JComponent {
    val checkoutLink = AnActionLink("CodeReview.Branch.Checkout", ACTION_LINKS_PLACE).apply {
      autoHideOnDisable = false
      bindEnabledIn(scope, branchesVm.isCheckoutInProgress.map { !it })
    }
    val worktreeLink = AnActionLink("CodeReview.Branch.CheckoutInNewWorktree", ACTION_LINKS_PLACE).apply {
      autoHideOnDisable = false
      bindEnabledIn(scope, branchesVm.isCheckoutInProgress.map { !it })
      isVisible = branchesVm.canCheckoutInNewWorktree
    }
    scope.launch {
      branchesVm.isCheckedOut.collect {
        worktreeLink.isVisible = branchesVm.canCheckoutInNewWorktree
      }
    }
    // one layout for all links aligns them by the baseline. The timeline and More links stay, and the other links hide when they do not fit.
    val panel = JPanel(HidingRowLayout(ACTION_LINK_GAP)).apply {
      isOpaque = false
      // a hidden component has the zero size and must not get the focus
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
        override fun accept(component: Component): Boolean = component.width > 0 && super.accept(component)
      }
      add(timelineLink)
      add(checkoutLink)
      add(worktreeLink)
      add(createMoreActionsLink(scope, branchesVm))
    }

    return UiDataProvider.wrapComponent(panel) { sink ->
      sink[CodeReviewBranchActionDataKeys.VIEW_MODEL] = branchesVm
    }
  }

  private fun createMoreActionsLink(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent {
    val moreAction = DumbAwareAction.create(AllIcons.Actions.More) {
      branchesVm.showBranches()
    }
    val moreLink = ActionButton(moreAction, null, MORE_ACTIONS_PLACE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
      accessibleContext.accessibleName = CollaborationToolsBundle.message("review.details.more.action")
    }

    scope.launch {
      branchesVm.showBranchesRequests.collectLatest { branches ->
        val point = RelativePoint.getSouthWestOf(moreLink)
        val dataContext = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(moreLink))
          .add(CodeReviewBranchActionDataKeys.VIEW_MODEL, branchesVm)
          .add(CodeReviewBranchActionDataKeys.BRANCHES, branches)
          .build()
        val actions = ActionManager.getInstance().getAction("CodeReview.Branch.Actions") as ActionGroup
        JBPopupFactory.getInstance().createActionGroupPopup(
          null, actions, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true
        ).showAndAwait(point, ShowDirection.BELOW)
      }
    }

    return moreLink
  }

  /**
   * Places the components in a row and aligns them by the baseline.
   * The first and the last components always show.
   * The middle components show from the start while they fit fully. The remaining components get the zero size.
   */
  private class HidingRowLayout(private val unscaledGap: Int) : LayoutManager {
    private val gap: Int
      get() = JBUI.scale(unscaledGap)

    override fun addLayoutComponent(name: String?, comp: Component?) = Unit

    override fun removeLayoutComponent(comp: Component?) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension = getLayoutSize(parent, minimum = false)

    override fun minimumLayoutSize(parent: Container): Dimension = getLayoutSize(parent, minimum = true)

    override fun layoutContainer(parent: Container) {
      val insets = parent.insets
      val allComponents = parent.components
      val children = allComponents.filter { it.isVisible }
      allComponents.filterNot { it.isVisible }.forEach { it.setBounds(0, 0, 0, 0) }
      if (children.isEmpty()) return

      val shownChildren = children.toMutableList()
      val availableWidth = parent.width - insets.left - insets.right
      while (shownChildren.size > 2 && getRowWidth(shownChildren) > availableWidth) {
        shownChildren.removeAt(shownChildren.lastIndex - 1).setBounds(0, 0, 0, 0)
      }

      val sizes = getSizes(shownChildren, minimum = false)
      val baselines = getBaselines(shownChildren, sizes)
      val rowHeight = getRowHeight(baselines, sizes)
      val rowBaseline = baselines.values.maxOrNull() ?: 0
      val availableHeight = parent.height - insets.top - insets.bottom
      val top = insets.top + (availableHeight - rowHeight).coerceAtLeast(0) / 2
      var x = insets.left
      for (child in shownChildren) {
        val size = sizes.getValue(child)
        child.setBounds(x, top + rowBaseline - baselines.getValue(child), size.width, size.height)
        x += size.width + gap
      }
    }

    private fun getLayoutSize(parent: Container, minimum: Boolean): Dimension {
      val insets = parent.insets
      val children = parent.components.filter { it.isVisible }
      val widthChildren = if (minimum && children.isNotEmpty()) listOf(children.first(), children.last()).distinct() else children
      val width = getRowWidth(widthChildren, minimum)
      val sizes = getSizes(children, minimum)
      val baselines = getBaselines(children, sizes)
      val height = getRowHeight(baselines, sizes)
      return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
    }

    private fun getRowWidth(children: List<Component>, minimum: Boolean = false): Int =
      children.sumOf { getSize(it, minimum).width } + gap * (children.size - 1).coerceAtLeast(0)

    private fun getSizes(children: List<Component>, minimum: Boolean): Map<Component, Dimension> =
      children.associateWith { getSize(it, minimum) }

    private fun getSize(component: Component, minimum: Boolean): Dimension =
      if (minimum) component.minimumSize else component.preferredSize

    private fun getBaselines(children: List<Component>, sizes: Map<Component, Dimension>): Map<Component, Int> {
      val textBaselines = children.associateWith { child ->
        val size = sizes.getValue(child)
        child.getBaseline(size.width, size.height)
      }.filterValues { it >= 0 }
      val textAscent = textBaselines.values.maxOrNull() ?: 0
      val textDescent = textBaselines.maxOfOrNull { entry -> sizes.getValue(entry.key).height - entry.value } ?: 0
      return children.associateWith { child ->
        textBaselines[child] ?: ((sizes.getValue(child).height + textAscent - textDescent) / 2)
      }
    }

    private fun getRowHeight(baselines: Map<Component, Int>, sizes: Map<Component, Dimension>): Int {
      if (baselines.isEmpty()) return 0
      val ascent = baselines.values.maxOrNull() ?: 0
      val descent = baselines.maxOf { entry -> sizes.getValue(entry.key).height - entry.value }
      return ascent + descent
    }
  }

  private const val ACTION_LINK_GAP = 8
  private const val ACTION_LINKS_PLACE = "CodeReview.Branch.Links"
  private const val MORE_ACTIONS_PLACE = "CodeReview.Branch.More"
}
