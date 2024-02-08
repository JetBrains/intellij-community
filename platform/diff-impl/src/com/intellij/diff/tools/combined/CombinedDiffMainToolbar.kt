// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.impl.ui.DiffToolChooser
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

internal class CombinedDiffMainToolbar(
  private val targetComponent: JComponent,
  private val diffToolChooser: DiffToolChooser,
  private val goToChangeAction: AnAction?,
  private val context: DiffContext
) {
  private val searchPanel = Wrapper()
  private val diffInfoPanel = Wrapper()

  private val leftToolbarPanel: Centerizer
  private val rightToolbarPanel: Centerizer

  private val leftToolbarGroup = DefaultActionGroup()
  private val leftToolbar: ActionToolbar

  private val rightToolbarGroup = DefaultActionGroup()
  private val rightToolbar: ActionToolbar

  private val panel: BorderLayoutPanel = BorderLayoutPanel()

  val component: JComponent = panel

  private val topPanel: BorderLayoutPanel

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

    topPanel = buildTopPanel()
    configureTopPanelForActionsMode()
    panel.addToTop(topPanel)
      .addToBottom(searchPanel)
  }

  fun setSearchComponent(searchComponent: JComponent) {
    searchPanel.setContent(searchComponent)
    searchComponent.border = SideBorder(JBColor.border(), SideBorder.LEFT)
    topPanel.removeAll()
    topPanel.addToCenter(searchPanel).addToLeft(leftToolbarPanel)
    configureTopPanelForSearchMode()
    revalidateAndRepaint()
  }

  fun hideSearch() {
    searchPanel.setContent(null)
    topPanel.removeAll()
    topPanel.addToCenter(diffInfoPanel).addToLeft(leftToolbarPanel).addToRight(rightToolbarPanel)
    configureTopPanelForActionsMode()
    revalidateAndRepaint()
  }

  private fun configureTopPanelForActionsMode() {
    topPanel.border = JBUI.Borders.empty(CombinedDiffUI.MAIN_HEADER_INSETS)
    val background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    topPanel.background = background
    leftToolbarPanel.background = background
    leftToolbar.component.background = background
  }

  private fun configureTopPanelForSearchMode() {
    topPanel.border = JBUI.Borders.emptyLeft(CombinedDiffUI.MAIN_HEADER_INSETS.left)
    val background = CombinedDiffUI.BLOCK_HEADER_BACKGROUND
    topPanel.background = background
    leftToolbarPanel.background = background
    leftToolbar.component.background = background
    leftToolbar.component.border = JBUI.Borders.emptyRight(CombinedDiffUI.MAIN_HEADER_INSETS.left)
  }

  fun getSearchDataProvider(): DataProvider? = DataManager.getDataProvider(searchPanel)

  fun isFocusedInWindow(): Boolean = DiffUtil.isFocusedComponentInWindow(leftToolbar.component) || DiffUtil.isFocusedComponentInWindow(
    rightToolbar.component)

  fun getPreferredFocusedComponent(): JComponent? {
    val component = leftToolbar.component
    if (component.isShowing) return component
    return null
  }

  fun updateDiffInfo(diffInfo: FrameDiffTool.DiffInfo?) {
    if (diffInfo != null) {
      val component = diffInfo.component
      component.background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
      val centerizer = Centerizer(component, Centerizer.TYPE.BOTH)
      diffInfoPanel.setContent(centerizer)
    }
    else {
      diffInfoPanel.setContent(null)
    }
  }

  fun clear() {
    diffInfoPanel.setContent(null)
    leftToolbarGroup.removeAll()
    rightToolbarGroup.removeAll()
  }

  private fun buildTopPanel(): BorderLayoutPanel {
    val topPanel = JBUI.Panels.simplePanel(diffInfoPanel)
      .andOpaque()
      .addToLeft(leftToolbarPanel)
      .addToRight(rightToolbarPanel)
    GuiUtils.installVisibilityReferent(topPanel, leftToolbar.component)
    GuiUtils.installVisibilityReferent(topPanel, rightToolbar.component)

    return topPanel
  }

  private fun revalidateAndRepaint() {
    panel.revalidate()
    panel.repaint()
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
    diffInfoPanel.setVerticalSizeReferent(component)
  }

  private val openInEditorAction = object : OpenInEditorAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }
}