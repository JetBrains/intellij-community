// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.CommonBundle
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.impl.DiffRequestProcessor.getToolOrderFromSettings
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.impl.ui.DiffToolChooser
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.search.CombinedDiffSearchContext
import com.intellij.diff.tools.combined.search.CombinedDiffSearchController
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.ui.GuiUtils
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.Centerizer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.max

class CombinedDiffMainUI(private val model: CombinedDiffModel, private val goToChangeAction: AnAction?) : Disposable {
  private val ourDisposable = Disposer.newCheckedDisposable().also { Disposer.register(this, it) }

  private val context: DiffContext = model.context
  private val settings = DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE))

  private val combinedToolOrder = arrayListOf<CombinedDiffTool>()

  private val popupActionGroup = DefaultActionGroup()
  private val touchbarActionGroup = DefaultActionGroup()
  private val mainPanel = MyMainPanel()

  private val contentPanel = Wrapper()

  private val diffToolChooser: MyDiffToolChooser = MyDiffToolChooser()

  private val mainToolbar: CombinedDiffMainToolbar = CombinedDiffMainToolbar(
    mainPanel,
    diffToolChooser,
    goToChangeAction,
    context
  )

  private val combinedViewer get() = context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  //
  // Global, shortcuts only navigation actions
  //

  private val openInEditorAction = object : OpenInEditorAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }

  private var searchController: CombinedDiffSearchController? = null

  init {
    Touchbar.setActions(mainPanel, touchbarActionGroup)
    updateAvailableDiffTools()

    val bottomContentSplitter = JBSplitter(true, "CombinedDiff.BottomComponentSplitter", 0.8f)
    bottomContentSplitter.firstComponent = contentPanel

    mainPanel.add(mainToolbar.component, BorderLayout.NORTH)
    mainPanel.add(bottomContentSplitter, BorderLayout.CENTER)

    mainPanel.isFocusTraversalPolicyProvider = true
    mainPanel.focusTraversalPolicy = MyFocusTraversalPolicy()

    val bottomPanel = context.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL)
    if (bottomPanel != null) bottomContentSplitter.secondComponent = bottomPanel
    if (bottomPanel is Disposable) Disposer.register(ourDisposable, bottomPanel)

    contentPanel.setContent(DiffUtil.createMessagePanel(CommonBundle.getLoadingTreeNodeText()))
  }

  @RequiresEdt
  internal fun setContent(viewer: CombinedDiffViewer, blockState: BlockState) {
    clear()
    contentPanel.setContent(viewer.component)
    val toolbarComponents = viewer.init()
    mainToolbar.updateDiffInfo(toolbarComponents.diffInfo)
    mainToolbar.updateToolbar(blockState, toolbarComponents.toolbarActions)
    buildActionPopup(toolbarComponents.popupActions)
  }

  fun setToolbarVerticalSizeReferent(component: JComponent) {
    mainToolbar.setVerticalSizeReferent(component)
  }

  @RequiresEdt
  fun setSearchController(searchController: CombinedDiffSearchController) {
    this.searchController = searchController
    mainToolbar.setSearchComponent(searchController.searchComponent)
  }

  @RequiresEdt
  fun updateSearch(context: CombinedDiffSearchContext) {
    searchController?.update(context)
  }

  @RequiresEdt
  fun closeSearch() {
    searchController = null
    mainToolbar.hideSearch()

    val project = model.context.project ?: return
    combinedViewer?.preferredFocusedComponent?.let { preferedFocusedComponent ->
      IdeFocusManager.getInstance(project).requestFocus(preferedFocusedComponent, false)
    }
  }

  fun getPreferredFocusedComponent(): JComponent? = mainToolbar.getPreferredFocusedComponent()

  fun getComponent(): JComponent = mainPanel

  fun isUnified() = diffToolChooser.getActiveTool() is CombinedUnifiedDiffTool

  fun isFocusedInWindow(): Boolean {
    return DiffUtil.isFocusedComponentInWindow(contentPanel) || mainToolbar.isFocusedInWindow()
  }

  fun isWindowFocused(): Boolean {
    val window = SwingUtilities.getWindowAncestor(mainPanel)
    return window != null && window.isFocused
  }

  fun requestFocusInWindow() {
    DiffUtil.requestFocusInWindow(getPreferredFocusedComponent())
  }

  private fun buildActionPopup(viewerActions: List<AnAction?>?) {
    collectPopupActions(viewerActions)
    DiffUtil.registerAction(ShowActionGroupPopupAction(), mainPanel)
  }

  private fun collectPopupActions(viewerActions: List<AnAction?>?) {
    popupActionGroup.removeAll()

    DiffUtil.addActionBlock(popupActionGroup, diffToolChooser)
    DiffUtil.addActionBlock(popupActionGroup, viewerActions)
  }

  private fun clear() {
    contentPanel.setContent(null)
    mainToolbar.clear()
    popupActionGroup.removeAll()
    ActionUtil.clearActions(mainPanel)
  }

  private fun moveToolOnTop(tool: CombinedDiffTool) {
    if (combinedToolOrder.remove(tool)) {
      combinedToolOrder.add(0, tool)
      updateToolOrderSettings(combinedToolOrder)
    }
  }

  private fun updateToolOrderSettings(toolOrder: List<DiffTool>) {
    val savedOrder = arrayListOf<String>()
    for (tool in toolOrder) {
      savedOrder.add(tool.javaClass.canonicalName)
    }
    settings.diffToolsOrder = savedOrder
  }

  private fun updateAvailableDiffTools() {
    combinedToolOrder.clear()
    val availableCombinedDiffTools = DiffManagerEx.getInstance().diffTools.filterIsInstance<CombinedDiffTool>()
    combinedToolOrder.addAll(getToolOrderFromSettings(settings, availableCombinedDiffTools).filterIsInstance<CombinedDiffTool>())
  }

  override fun dispose() {
    if (ourDisposable.isDisposed) return
    UIUtil.invokeLaterIfNeeded {
      if (ourDisposable.isDisposed) return@invokeLaterIfNeeded
      clear()
    }
  }

  private inner class MyDiffToolChooser : DiffToolChooser(context.project) {
    private val availableTools = arrayListOf<CombinedDiffTool>().apply {
      addAll(DiffManagerEx.getInstance().diffTools.filterIsInstance<CombinedDiffTool>())
    }

    private var activeTool: CombinedDiffTool = combinedToolOrder.firstOrNull() ?: availableTools.first()

    override fun onSelected(project: Project, diffTool: DiffTool) {
      val combinedDiffTool = diffTool as? CombinedDiffTool ?: return

      DiffUsageTriggerCollector.logToggleDiffTool(project, diffTool, context.getUserData(DiffUserDataKeys.PLACE))
      activeTool = combinedDiffTool

      moveToolOnTop(diffTool)
      model.reload()
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return super.createCustomComponent(presentation, place).apply {
        background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
      }
    }

    override fun getTools(): List<CombinedDiffTool> = availableTools.toList()

    override fun getActiveTool(): DiffTool = activeTool

    override fun getForcedDiffTool(): DiffTool? = null
  }

  private inner class MyMainPanel : JBPanelWithEmptyText(BorderLayout()), DataProvider {
    init {
      background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    }

    override fun getPreferredSize(): Dimension {
      val windowSize = DiffUtil.getDefaultDiffPanelSize()
      val size = super.getPreferredSize()
      return Dimension(max(windowSize.width, size.width), max(windowSize.height, size.height))
    }

    override fun getData(dataId: @NonNls String): Any? {
      val data = DataManagerImpl.getDataProviderEx(contentPanel.targetComponent)?.getData(dataId)
      if (data != null) return data

      return when {
        DiffDataKeys.DIFF_REQUEST.`is`(dataId) -> getCurrentRequest()
        OpenInEditorAction.AFTER_NAVIGATE_CALLBACK.`is`(dataId) -> Runnable {  DiffUtil.minimizeDiffIfOpenedInWindow(this)}
        CommonDataKeys.PROJECT.`is`(dataId) -> context.project
        PlatformCoreDataKeys.HELP_ID.`is`(dataId) -> {
          if (context.getUserData(DiffUserDataKeys.HELP_ID) != null) {
            context.getUserData(DiffUserDataKeys.HELP_ID)
          }
          else {
            "reference.dialogs.diff.file"
          }
        }
        DiffDataKeys.DIFF_CONTEXT.`is`(dataId) -> context
        else -> getCurrentRequest()?.getUserData(DiffUserDataKeys.DATA_PROVIDER)?.getData(dataId)
                ?: context.getUserData(DiffUserDataKeys.DATA_PROVIDER)?.getData(dataId)
      }
    }
  }

  fun getCurrentRequest(): DiffRequest? {
    val id = combinedViewer?.getCurrentBlockId() ?: return null
    return model.getLoadedRequest(id)
  }

  private inner class ShowActionGroupPopupAction : DumbAwareAction() {
    init {
      ActionUtil.copyFrom(this, "Diff.ShowSettingsPopup")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = popupActionGroup.childrenCount > 0
    }

    override fun actionPerformed(e: AnActionEvent) {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(DiffBundle.message("diff.actions"), popupActionGroup, e.dataContext,
                                                                      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
      popup.showInCenterOf(mainPanel)
    }
  }

  private inner class MyFocusTraversalPolicy : IdeFocusTraversalPolicy() {
    override fun getDefaultComponent(focusCycleRoot: Container): Component? {
      val component: JComponent = getPreferredFocusedComponent() ?: return null
      return getPreferredFocusedComponent(component, this)
    }

    override fun getProject() = context.project
  }
}

private class CombinedDiffMainToolbar(
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

    panel.addToTop(buildTopPanel())
      .addToBottom(searchPanel)
  }

  fun setSearchComponent(searchComponent: JComponent) {
    searchPanel.setContent(searchComponent)
    revalidateAndRepaint()
  }

  fun hideSearch() {
    searchPanel.setContent(null)
    revalidateAndRepaint()
  }

  fun isFocusedInWindow(): Boolean = DiffUtil.isFocusedComponentInWindow(leftToolbar.component) || DiffUtil.isFocusedComponentInWindow(rightToolbar.component)

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
      .andTransparent()
      .addToLeft(leftToolbarPanel)
      .addToRight(rightToolbarPanel)
      .apply {
        border = JBUI.Borders.empty(CombinedDiffUI.MAIN_HEADER_INSETS)
      }
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

  fun setVerticalSizeReferent(component: javax.swing.JComponent) {
    diffInfoPanel.setVerticalSizeReferent(component)
  }

  private val openInEditorAction = object : OpenInEditorAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }
}