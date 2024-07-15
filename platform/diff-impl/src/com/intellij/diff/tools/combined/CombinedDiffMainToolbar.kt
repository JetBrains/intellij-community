// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.impl.ui.DiffToolChooser
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

internal class CombinedDiffMainToolbar(
  private val cs: CoroutineScope,
  private val uiState: CombinedDiffUIState,
  private val targetComponent: JComponent,
  private val diffToolChooser: DiffToolChooser,
  private val goToChangeAction: AnAction?,
  private val context: DiffContext
) {
  private val searchPanel = Wrapper()
  private val diffInfoPanel = DiffInfoComponent()

  private val leftToolbarPanel: Centerizer
  private val rightToolbarPanel: Centerizer

  private val leftToolbarGroup = DefaultActionGroup()
  private val leftToolbar: ActionToolbar

  private val rightToolbarGroup = DefaultActionGroup()
  private val rightToolbar: ActionToolbar

  private val topPanel: JPanel = JPanel(null)
  val component: JComponent = topPanel

  init {
    leftToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, leftToolbarGroup, true)
    leftToolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    leftToolbar.targetComponent = targetComponent
    leftToolbar.component.background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    leftToolbar.component.border = JBUI.Borders.empty()
    leftToolbarPanel = Centerizer(leftToolbar.component, Centerizer.TYPE.VERTICAL)
    context.putUserData(DiffUserDataKeysEx.LEFT_TOOLBAR, leftToolbar)

    rightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_RIGHT_TOOLBAR, rightToolbarGroup, true)
    rightToolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    rightToolbar.targetComponent = targetComponent
    rightToolbar.component.background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    rightToolbar.component.border = JBUI.Borders.empty()
    rightToolbarPanel = Centerizer(rightToolbar.component, Centerizer.TYPE.VERTICAL)

    GuiUtils.installVisibilityReferent(topPanel, leftToolbar.component)
    GuiUtils.installVisibilityReferent(topPanel, rightToolbar.component)
    configureTopPanelForActionsMode()

    cs.launch {
      uiState.diffInfoStateFlow.collectLatest { diffInfo ->
        diffInfoPanel.updateForState(diffInfo)
      }
    }
  }

  fun setSearchComponent(searchComponent: JComponent) {
    searchPanel.setContent(searchComponent)
    searchComponent.border = SideBorder(JBColor.border(), SideBorder.LEFT)
    configureTopPanelForSearchMode()
    revalidateAndRepaint()
  }

  fun hideSearch() {
    searchPanel.setContent(null)
    configureTopPanelForActionsMode()
    revalidateAndRepaint()
  }

  private fun configureTopPanelForActionsMode() {
    topPanel.apply {
      removeAll()
      layout = MainToolbarLayout(leftToolbarPanel, rightToolbarPanel, diffInfoPanel)
      add(leftToolbarPanel)
      add(rightToolbarPanel)
      add(diffInfoPanel.panel)
    }
    topPanel.border = JBUI.Borders.empty(CombinedDiffUI.MAIN_HEADER_INSETS)
    val background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    topPanel.background = background
    leftToolbarPanel.background = background
    leftToolbar.component.background = background
  }

  private fun configureTopPanelForSearchMode() {
    topPanel.apply {
      removeAll()
      layout = BorderLayout()
      add(leftToolbarPanel, BorderLayout.WEST)
      add(searchPanel, BorderLayout.CENTER)
    }
    topPanel.border = JBUI.Borders.emptyLeft(CombinedDiffUI.MAIN_HEADER_INSETS.left)
    val background = CombinedDiffUI.BLOCK_HEADER_BACKGROUND
    topPanel.background = background
    leftToolbarPanel.background = background
    leftToolbar.component.background = background
    leftToolbar.component.border = JBUI.Borders.emptyRight(CombinedDiffUI.MAIN_HEADER_INSETS.left)
  }

  fun isFocusedInWindow(): Boolean = DiffUtil.isFocusedComponentInWindow(leftToolbar.component) || DiffUtil.isFocusedComponentInWindow(rightToolbar.component)

  fun getPreferredFocusedComponent(): JComponent? {
    val component = leftToolbar.component
    if (component.isShowing) return component
    return null
  }

  fun clear() {
    leftToolbarGroup.removeAll()
    rightToolbarGroup.removeAll()
  }

  private fun revalidateAndRepaint() {
    component.revalidate()
    component.repaint()
  }

  fun updateToolbar(blockState: BlockState, toolbarActions: List<AnAction>?) {
    collectToolbarActions(blockState, toolbarActions)
    (leftToolbar as ActionToolbarImpl).reset()
    leftToolbar.updateActionsImmediately()
    DiffUtil.recursiveRegisterShortcutSet(leftToolbarGroup, targetComponent, null)
    (rightToolbar as ActionToolbarImpl).reset()
    rightToolbar.updateActionsImmediately()

    DiffUtil.recursiveRegisterShortcutSet(rightToolbarGroup, targetComponent, null)
  }

  private fun collectToolbarActions(blockState: BlockState, viewerActions: List<AnAction?>?) {
    leftToolbarGroup.removeAll()
    val navigationActions = ArrayList<AnAction>(collectNavigationActions(blockState))

    rightToolbarGroup.add(diffToolChooser)

    DiffUtil.addActionBlock(leftToolbarGroup, navigationActions)

    DiffUtil.addActionBlock(rightToolbarGroup, viewerActions, false)
    val contextActions = context.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS)
    DiffUtil.addActionBlock(leftToolbarGroup, contextActions)
  }

  private fun collectNavigationActions(blockState: BlockState): List<AnAction> {
    return listOfNotNull(
      CombinedPrevBlockAction(context),
      CombinedPrevDifferenceAction(context),
      FilesLabelAction(goToChangeAction, leftToolbar.component, blockState),
      CombinedNextDifferenceAction(context),
      CombinedNextBlockAction(context),
      openInEditorAction,
    )
  }

  fun setVerticalSizeReferent(component: JComponent) {
    //diffInfoPanel.setVerticalSizeReferent(component)
  }

  private val openInEditorAction = object : OpenInEditorAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }
}

/**
 * The goal of this layout is to center [DiffInfoComponent] in the way that its logical (e.g sarrow icon) center is always in the center of toolbar
 */
private class MainToolbarLayout(
  private val left: JComponent,
  private val right: JComponent,
  private val diffInfo: DiffInfoComponent,
) : LayoutManager2 {
  override fun addLayoutComponent(comp: Component?, constraints: Any?) {}
  override fun addLayoutComponent(name: String?, comp: Component?) {}
  override fun removeLayoutComponent(comp: Component?) {}

  override fun preferredLayoutSize(parent: Container): Dimension = size(parent)
  override fun minimumLayoutSize(parent: Container): Dimension = Dimension(100, 0)
  override fun maximumLayoutSize(target: Container): Dimension = size(target)

  private fun size(parent: Container): Dimension {
    val w = parent.width
    return Dimension(w, maxOfHeights() + parent.insets.top + parent.insets.bottom)
  }

  private fun maxOfHeights(): Int = max(diffInfo.panel.preferredSize.height,
                                        max(left.preferredSize.height, right.preferredSize.height))

  override fun layoutContainer(parent: Container) {
    val bounds = parent.bounds
    JBInsets.removeFrom(bounds, parent.insets)
    val h = bounds.height
    val w = bounds.width

    val leftSize = left.preferredSize
    left.bounds = Rectangle(bounds.x, bounds.y, leftSize.width, h)

    val rightSize = right.preferredSize
    val rightX = bounds.x + w - rightSize.width

    right.bounds = Rectangle(rightX, bounds.y, rightSize.width, h)

    val centerSize: Dimension = diffInfo.panel.preferredSize

    var infoX = bounds.x + (w / 2 - diffInfo.getCenterX())

    val gap = JBUIScale.scale(2)
    if (infoX + centerSize.width >= rightX - gap) {
      infoX = rightX - centerSize.width - gap
    }
    if (infoX <= bounds.x + left.width + gap) {
      infoX = bounds.x + left.width + gap
    }
    diffInfo.panel.bounds = Rectangle(infoX, bounds.y, centerSize.width, h)
  }

  override fun getLayoutAlignmentX(target: Container?): Float = Component.LEFT_ALIGNMENT

  override fun getLayoutAlignmentY(target: Container?): Float = Component.CENTER_ALIGNMENT

  override fun invalidateLayout(target: Container?) {}
}

private class DiffInfoComponent {
  private val leftTitle = JBLabel().setCopyable(true)
  private val rightLabel = JBLabel().setCopyable(true)
  private val arrows = JBLabel(AllIcons.Diff.ArrowLeftRight)
  private val gap = 10
  val panel = JPanel(HorizontalLayout(gap))

  init {
    panel.add(leftTitle)
    panel.add(arrows)
    panel.add(rightLabel)
  }

  fun updateForState(diffInfo: CombinedDiffUIState.DiffInfoState) {
    when (diffInfo) {
      CombinedDiffUIState.DiffInfoState.Empty -> {
        leftTitle.isVisible = false
        arrows.isVisible = false
        rightLabel.isVisible = false
      }
      is CombinedDiffUIState.DiffInfoState.SingleTitle -> {
        leftTitle.isVisible = true
        arrows.isVisible = false
        rightLabel.isVisible = false
        leftTitle.text = diffInfo.title
      }
      is CombinedDiffUIState.DiffInfoState.TwoTitles -> {
        leftTitle.isVisible = true
        arrows.isVisible = true
        rightLabel.isVisible = true
        leftTitle.text = diffInfo.leftTitle
        rightLabel.text = diffInfo.rightTitle
      }
    }
  }

  fun getCenterX(): Int {
    if (arrows.isVisible) {
      return leftTitle.preferredSize.width + JBUIScale.scale(gap) + arrows.preferredSize.width / 2
    }
    return panel.preferredSize.width / 2
  }
}
