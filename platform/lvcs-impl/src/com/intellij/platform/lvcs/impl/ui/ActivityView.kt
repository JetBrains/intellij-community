// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.find.SearchTextArea
import com.intellij.find.editorHeaderActions.Utils
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.settings.ActivityViewApplicationSettings
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.ui.SingleFileActivityDiffPreview.Companion.DIFF_PLACE
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent

class ActivityView(private val project: Project, gateway: IdeaGateway, val activityScope: ActivityScope,
                   private val isFrameDiffPreview: Boolean = false) :
  JBPanel<ActivityView>(BorderLayout()), DataProvider, Disposable {

  private val coroutineScope = project.service<ActivityService>().coroutineScope.childScope()

  private val model = ActivityViewModel(project, gateway, activityScope, coroutineScope)

  private val activityList = ActivityList { model.activityProvider.getPresentation(it) }.apply {
    updateEmptyText(true)
  }
  private val changesBrowser = createChangesBrowser()
  private val editorDiffPreview = if (!isFrameDiffPreview) createEditorDiffPreview(changesBrowser) else null

  private val changesSplitter: TwoKeySplitter

  init {
    PopupHandler.installPopupMenu(activityList, "ActivityView.Popup", "ActivityView.Popup")
    val scrollPane = ScrollPaneFactory.createScrollPane(activityList,
                                                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    val progressStripe = ProgressStripe(scrollPane, this)

    val toolbarComponent = BorderLayoutPanel()

    val filterProgress = if (model.isScopeFilterSupported || model.isActivityFilterSupported) {
      val searchField = createSearchField()
      object : ProgressBarLoadingDecorator(searchField, this@ActivityView, 500) {
        override fun isOnTop() = false
      }.also {
        toolbarComponent.add(it.component, BorderLayout.CENTER)
      }
    }
    else null

    val toolbarGroup = ActionManager.getInstance().getAction("ActivityView.Toolbar") as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar("ActivityView.Toolbar", toolbarGroup, true)
    toolbar.targetComponent = this
    toolbar.setReservePlaceAutoPopupIcon(false)
    toolbarComponent.add(toolbar.component, BorderLayout.EAST)

    val mainComponent = BorderLayoutPanel()
    mainComponent.add(progressStripe, BorderLayout.CENTER)
    mainComponent.add(toolbarComponent, BorderLayout.NORTH)

    changesSplitter = TwoKeySplitter(true,
                                     ProportionKey("lvcs.changes.splitter.vertical", 0.5f,
                                                   "lvcs.changes.splitter.horizontal", 0.5f))
    changesSplitter.firstComponent = mainComponent
    changesSplitter.secondComponent = changesBrowser

    val diffSplitter = OnePixelSplitter(false, "lvcs.diff.splitter.horizontal", 0.2f)
    diffSplitter.firstComponent = changesSplitter
    diffSplitter.secondComponent = if (editorDiffPreview == null) createFrameDiffPreview(changesBrowser).component else null

    add(diffSplitter, BorderLayout.CENTER)

    activityList.addListener(object : ActivityList.Listener {
      override fun onSelectionChanged(selection: ActivitySelection) {
        model.setSelection(selection)
      }
      override fun onEnter(): Boolean = showDiff()
      override fun onDoubleClick(): Boolean = showDiff()
    }, this)
    model.addListener(object : ActivityModelListener {
      override fun onItemsLoadingStarted() {
        activityList.updateEmptyText(true)
        progressStripe.startLoading()
      }
      override fun onItemsLoadingStopped(data: ActivityData) {
        activityList.setData(data)
        activityList.updateEmptyText(false)
        progressStripe.stopLoading()
      }
      override fun onFilteringStarted() {
        filterProgress?.startLoading(false)
        activityList.updateEmptyText(true)
      }
      override fun onFilteringStopped(result: Set<ActivityItem>?) {
        filterProgress?.stopLoading()
        activityList.setVisibleItems(result)
        activityList.updateEmptyText(false)
      }
    }, this)
  }

  override fun getData(dataId: String): Any? {
    if (ActivityViewDataKeys.SELECTION.`is`(dataId)) return activityList.selection
    if (ActivityViewDataKeys.SCOPE.`is`(dataId)) return activityScope
    if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.`is`(dataId)) return editorDiffPreview
    return null
  }

  val preferredFocusedComponent: JComponent get() = activityList

  private fun createChangesBrowser(): ActivityChangesBrowser? {
    if (model.isSingleDiffSupported) return null

    val changesBrowser = ActivityChangesBrowser(project)
    model.addListener(object : ActivityModelListener {
      override fun onDiffDataLoadingStarted() {
        changesBrowser.updateEmptyText(true)
      }
      override fun onDiffDataLoadingStopped(diffData: ActivityDiffData?) {
        changesBrowser.updateEmptyText(false)
        changesBrowser.diffData = diffData
      }
    }, changesBrowser)
    changesBrowser.updateEmptyText(true)
    Disposer.register(this, changesBrowser)
    return changesBrowser
  }

  private fun createEditorDiffPreview(changesBrowser: ActivityChangesBrowser?): DiffPreview {
    if (changesBrowser != null) {
      val diffPreview = MultiFileActivityDiffPreview(activityScope, changesBrowser.viewer, this@ActivityView)
      changesBrowser.setShowDiffActionPreview(diffPreview)
      return diffPreview
    }

    return SingleFileActivityDiffPreview(project, model, this@ActivityView)
  }

  private fun createFrameDiffPreview(changesBrowser: ActivityChangesBrowser?): DiffEditorViewer {
    val diffViewer = if (changesBrowser != null) {
      TreeHandlerEditorDiffPreview.createDefaultViewer(changesBrowser.viewer, ActivityDiffPreviewHandler(), DIFF_PLACE)
    }
    else {
      SingleFileActivityDiffPreview.createViewer(project, model)
    }
    Disposer.register(this, diffViewer.disposable)
    return diffViewer
  }

  private fun createSearchField(): SearchTextArea {
    val textArea = JBTextArea()
    textArea.emptyText.text = if (model.isScopeFilterSupported) LocalHistoryBundle.message("activity.filter.empty.text.fileName")
    else if (model.isActivityFilterSupported) LocalHistoryBundle.message("activity.filter.empty.text.content")
    else ""
    TextComponentEmptyText.setupPlaceholderVisibility(textArea)

    val searchTextArea = SearchTextArea(textArea, true)
    searchTextArea.setBorder(JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.RIGHT), searchTextArea.border))
    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        IdeFocusManager.getInstance(project).requestFocus(searchTextArea.textArea, true)
      }
    }.registerCustomShortcutSet(Utils.shortcutSetOf(Utils.shortcutsOf(IdeActions.ACTION_FIND)), activityList)
    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        searchTextArea.textArea.text = ""
        IdeFocusManager.getInstance(project).requestFocus(activityList, true)
      }
    }.registerCustomShortcutSet(CustomShortcutSet(KeyEvent.VK_ESCAPE), searchTextArea.textArea)
    searchTextArea.textArea.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (!model.isFilterSet) LocalHistoryCounter.logFilterUsed(activityScope)
        model.setFilter(searchTextArea.textArea.getText())
      }
    })
    return searchTextArea
  }

  private fun ActivityList.updateEmptyText(isLoading: Boolean) = setEmptyText(getListEmptyText(isLoading))

  private fun getListEmptyText(isLoading: Boolean): @NlsContexts.StatusText String {
    if (isLoading) return LocalHistoryBundle.message("activity.empty.text.loading")
    if (model.isFilterSet) {
      if (activityScope is ActivityScope.Recent) {
        return LocalHistoryBundle.message("activity.list.empty.text.recent.matching")
      }
      return LocalHistoryBundle.message("activity.list.empty.text.in.scope.matching", activityScope.presentableName)
    }
    if (activityScope is ActivityScope.Recent) {
      return LocalHistoryBundle.message("activity.list.empty.text.recent")
    }
    return LocalHistoryBundle.message("activity.list.empty.text.in.scope", activityScope.presentableName)
  }

  private fun ActivityChangesBrowser.updateEmptyText(isLoading: Boolean) = viewer.setEmptyText(getBrowserEmptyText(isLoading))

  private fun getBrowserEmptyText(isLoading: Boolean): @NlsContexts.StatusText String {
    if (isLoading) return LocalHistoryBundle.message("activity.empty.text.loading")
    if (model.selection?.selectedItems.isNullOrEmpty()) {
      return LocalHistoryBundle.message("activity.browser.empty.text.no.selection")
    }
    return LocalHistoryBundle.message("activity.browser.empty.text")
  }

  internal fun showDiff(): Boolean {
    if (editorDiffPreview == null) return false
    return editorDiffPreview.performDiffAction()
  }

  internal fun setVertical(isVertical: Boolean) {
    changesSplitter.orientation = isVertical
  }

  override fun dispose() {
    coroutineScope.cancel()
  }

  companion object {
    @JvmStatic
    fun showInToolWindow(project: Project, gateway: IdeaGateway, activityScope: ActivityScope) {
      LocalHistoryCounter.logLocalHistoryOpened(activityScope)

      if (ActivityToolWindow.showTab(project) { content -> (content.component as? ActivityView)?.activityScope == activityScope }) {
        return
      }

      val activityView = ActivityView(project, gateway, activityScope)
      if (Registry.`is`("lvcs.open.diff.automatically") && !VcsEditorTabFilesManager.getInstance().shouldOpenInNewWindow) {
        activityView.openDiffWhenLoaded()
      }

      val content = ContentFactory.getInstance().createContent(activityView, activityScope.presentableName, false)
      content.preferredFocusableComponent = activityView.preferredFocusedComponent
      content.setDisposer(activityView)

      ActivityToolWindow.showTab(project, content)
      ActivityToolWindow.onContentVisibilityChanged(project, content, activityView) { isVisible ->
        activityView.model.setVisible(isVisible)
      }
      ActivityToolWindow.onOrientationChanged(project, activityView) { isVertical ->
        activityView.setVertical(isVertical)
      }
    }

    private fun ActivityView.openDiffWhenLoaded() {
      if (changesBrowser != null || isFrameDiffPreview) return

      val disposable = Disposer.newDisposable()
      Disposer.register(this, disposable)
      model.addListener(object : ActivityModelListener {
        override fun onItemsLoadingStopped(data: ActivityData) {
          if (data.items.isEmpty()) Disposer.dispose(disposable)
          else showDiff()
        }
      }, disposable)
    }

    @JvmStatic
    fun showInDialog(project: Project, gateway: IdeaGateway, activityScope: ActivityScope) {
      LocalHistoryCounter.logLocalHistoryOpened(activityScope)

      val activityView = ActivityView(project, gateway, activityScope, isFrameDiffPreview = true)

      val dialog = FrameWrapper(project,
                                title = LocalHistoryBundle.message("activity.dialog.title", activityScope.presentableName),
                                component = activityView,
                                dimensionKey = "lvcs.dialog.size")
      dialog.closeOnEsc()
      Disposer.register(dialog, activityView)
      dialog.show()
    }

    @JvmStatic
    fun isViewEnabled(): Boolean {
      if (!isViewAvailable()) return false
      return service<ActivityViewApplicationSettings>().isActivityToolWindowEnabled
    }

    @JvmStatic
    fun isViewAvailable(): Boolean {
      return ApplicationInfo.getInstance().isEAP || Registry.`is`("lvcs.show.activity.view")
    }
  }
}

@Service(Service.Level.PROJECT)
class ActivityService(val coroutineScope: CoroutineScope)
