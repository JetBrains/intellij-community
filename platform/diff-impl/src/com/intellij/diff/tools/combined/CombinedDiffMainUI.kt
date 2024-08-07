// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.CommonBundle
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffTool
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.lang.Runnable
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.max

@ApiStatus.Internal
class CombinedDiffMainUI(private val model: CombinedDiffModel, private val goToChangeAction: AnAction?) : Disposable {
  private val ourDisposable = Disposer.newCheckedDisposable().also { Disposer.register(this, it) }

  @OptIn(DelicateCoroutinesApi::class)
  private val cs: CoroutineScope = GlobalScope.childScope("CombinedDiffMainUI", Dispatchers.EDT)

  private val context: DiffContext = model.context
  private val settings = DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE))

  private val popupActionGroup = DefaultActionGroup()
  private val touchbarActionGroup = DefaultActionGroup()
  private val mainPanel = MyMainPanel()

  private val contentPanel = Wrapper()

  private val diffToolChooser: MyDiffToolChooser = MyDiffToolChooser(context, model, settings)

  private val combinedDiffUIState = CombinedDiffUIState()

  private val mainToolbar: CombinedDiffMainToolbar = CombinedDiffMainToolbar(
    cs,
    combinedDiffUIState,
    mainPanel,
    diffToolChooser,
    goToChangeAction,
    context
  )

  private val combinedViewer get() = context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  private var searchController: CombinedDiffSearchController? = null

  init {
    Disposer.register(ourDisposable) {
      cs.cancel()
    }

    Touchbar.setActions(mainPanel, touchbarActionGroup)

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
    mainToolbar.updateToolbar(blockState, toolbarComponents.toolbarActions)
    buildActionPopup(toolbarComponents.popupActions)
  }

  fun setToolbarVerticalSizeReferent(component: JComponent) {
    mainToolbar.setVerticalSizeReferent(component)
  }

  @RequiresEdt
  fun setSearchController(searchController: CombinedDiffSearchController) {
    this.searchController = searchController
    combinedDiffUIState.setSearchMode(true)
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
    combinedDiffUIState.setSearchMode(false)

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

  private fun buildActionPopup(popupActions: List<AnAction?>?) {
    popupActionGroup.removeAll()
    DiffUtil.addActionBlock(popupActionGroup, diffToolChooser)
    DiffUtil.addActionBlock(popupActionGroup, popupActions)
    DiffUtil.registerAction(ShowActionGroupPopupAction(mainPanel, popupActionGroup), mainPanel)
  }

  private fun clear() {
    combinedDiffUIState.reset()
    contentPanel.setContent(null)
    mainToolbar.clear()
    popupActionGroup.removeAll()
    ActionUtil.clearActions(mainPanel)
  }

  override fun dispose() {
    if (ourDisposable.isDisposed) return
    UIUtil.invokeLaterIfNeeded {
      if (ourDisposable.isDisposed) return@invokeLaterIfNeeded
      clear()
    }
  }

  private class MyDiffToolChooser(
    val context: DiffContext,
    val model: CombinedDiffModel,
    val settings: DiffSettings,
  ) : DiffToolChooser(context.project) {
    private val availableTools = arrayListOf<CombinedDiffTool>().apply {
      addAll(DiffManagerEx.getInstance().diffTools.filterIsInstance<CombinedDiffTool>())
    }

    private val combinedToolOrder = arrayListOf<CombinedDiffTool>()

    private var activeTool: CombinedDiffTool

    init {
      updateAvailableDiffTools()
      activeTool = combinedToolOrder.firstOrNull() ?: availableTools.first()
    }

    private fun updateAvailableDiffTools() {
      combinedToolOrder.clear()
      val availableCombinedDiffTools = DiffManagerEx.getInstance().diffTools.filterIsInstance<CombinedDiffTool>()
      combinedToolOrder.addAll(getToolOrderFromSettings(settings, availableCombinedDiffTools).filterIsInstance<CombinedDiffTool>())
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

  private inner class MyMainPanel : JBPanelWithEmptyText(BorderLayout()), UiDataProvider {
    init {
      background = CombinedDiffUI.MAIN_HEADER_BACKGROUND
    }

    override fun getPreferredSize(): Dimension {
      val windowSize = DiffUtil.getDefaultDiffPanelSize()
      val size = super.getPreferredSize()
      return Dimension(max(windowSize.width, size.width), max(windowSize.height, size.height))
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[DiffDataKeys.DIFF_REQUEST] = getCurrentRequest()
      sink[OpenInEditorAction.AFTER_NAVIGATE_CALLBACK] = Runnable { DiffUtil.minimizeDiffIfOpenedInWindow(this) }
      sink[CommonDataKeys.PROJECT] = context.project
      sink[PlatformCoreDataKeys.HELP_ID] = context.getUserData(DiffUserDataKeys.HELP_ID) ?: "reference.dialogs.diff.file"
      sink[DiffDataKeys.DIFF_CONTEXT] = context

      DataSink.uiDataSnapshot(sink, context.getUserData(DiffUserDataKeys.DATA_PROVIDER))
      DataSink.uiDataSnapshot(sink, getCurrentRequest()?.getUserData(DiffUserDataKeys.DATA_PROVIDER))
      DataSink.uiDataSnapshot(sink, contentPanel.targetComponent as? UiDataProvider
                                    ?: DataManagerImpl.getDataProviderEx(contentPanel.targetComponent))
    }
  }

  fun getCurrentRequest(): DiffRequest? {
    val id = combinedViewer?.getCurrentBlockId() ?: return null
    return model.getLoadedRequest(id)
  }

  fun getUiState(): CombinedDiffUIState = combinedDiffUIState

  private inner class MyFocusTraversalPolicy : IdeFocusTraversalPolicy() {
    override fun getDefaultComponent(focusCycleRoot: Container): Component? {
      val component: JComponent = getPreferredFocusedComponent() ?: return null
      return getPreferredFocusedComponent(component, this)
    }

    override fun getProject() = context.project
  }
}

private class ShowActionGroupPopupAction(
  private val parentComponent: JComponent,
  private val popupActionGroup: DefaultActionGroup
) : DumbAwareAction() {
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
    popup.showInCenterOf(parentComponent)
  }
}

/**
 * Various ui states which shared between the main ui and the combined diff viewer
 */
@ApiStatus.Experimental
class CombinedDiffUIState {
  private val searchMode: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val stickyHeaderUnderBorder: MutableStateFlow<Boolean> = MutableStateFlow(false)

  private val _diffInfoState: MutableStateFlow<DiffInfoState> = MutableStateFlow(DiffInfoState.Empty)

  val diffInfoStateFlow: StateFlow<DiffInfoState>
    get() = _diffInfoState

  val separatorState: Flow<Boolean> = combine(searchMode, stickyHeaderUnderBorder) { search, header -> search || header }

  fun setSearchMode(isSearchMode: Boolean) {
    searchMode.value = isSearchMode
  }

  fun setStickyHeaderUnderBorder(isHeaderUnderBorder: Boolean) {
    stickyHeaderUnderBorder.value = isHeaderUnderBorder
  }

  fun reset() {
    setStickyHeaderUnderBorder(false)
    setSearchMode(false)
    setDiffInfo(DiffInfoState.Empty)
  }

  fun setDiffInfo(diffInfoState: DiffInfoState) {
    _diffInfoState.value = diffInfoState
  }

  sealed class DiffInfoState {
    data object Empty : DiffInfoState()
    data class SingleTitle(@Nls val title: String) : DiffInfoState()
    data class TwoTitles(@Nls val leftTitle: String, @Nls val rightTitle: String) : DiffInfoState()
  }
}
