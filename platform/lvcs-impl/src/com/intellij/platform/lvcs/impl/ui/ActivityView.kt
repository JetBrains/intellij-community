// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.find.EditorSearchSession
import com.intellij.find.SearchTextArea
import com.intellij.find.editorHeaderActions.Utils
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.ui.views.FileHistoryDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
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
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

@ApiStatus.Internal
class ActivityView(private val project: Project, gateway: IdeaGateway, val activityScope: ActivityScope,
                   private val isFrameDiffPreview: Boolean = false) :
  JBPanel<ActivityView>(BorderLayout()), UiDataProvider, Disposable {

  private val coroutineScope = project.service<ActivityService>().coroutineScope.childScope("ActivityView")
  private val settings = service<ActivityViewApplicationSettings>()

  private val model = ActivityViewModel(project, gateway, activityScope, currentDiffMode, coroutineScope)

  private val activityList = ActivityList { model.activityProvider.getPresentation(it) }.apply {
    updateEmptyText(true)
  }
  private val searchField = createSearchField()
  private val changesBrowser = createChangesBrowser()
  private val frameDiffPreview = if (isFrameDiffPreview) createFrameDiffPreview(changesBrowser) else null
  private val editorDiffPreview = if (frameDiffPreview == null) createEditorDiffPreview(changesBrowser) else null

  private val changesSplitter: TwoKeySplitter

  private val currentDiffMode get() = if (isSwitchingDiffModeAllowed) settings.diffMode else DirectoryDiffMode.WithNext
  private val isSwitchingDiffModeAllowed get() = activityScope != ActivityScope.Recent

  init {
    PopupHandler.installPopupMenu(activityList, "ActivityView.Popup", "ActivityView.Popup")
    val scrollPane = ScrollPaneFactory.createScrollPane(activityList,
                                                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    val progressStripe = ProgressStripe(scrollPane, this)

    val toolbarComponent = BorderLayoutPanel()
    frameDiffPreview?.setToolbarVerticalSizeReferent(toolbarComponent)

    val filterProgress = searchField.let { field ->
      object : ProgressBarLoadingDecorator(field.containerComponent, this@ActivityView, 500) {
        override fun isOnTop() = false
      }.also {
        toolbarComponent.add(it.component, BorderLayout.CENTER)
      }
    }

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

    val diffSplitter = OnePixelSplitter(false,
                                        if (changesBrowser != null) "lvcs.diff.with.changes.splitter.horizontal" else "lvcs.diff.with.list.splitter.horizontal",
                                        if (changesBrowser != null) 0.4f else 0.2f)
    diffSplitter.firstComponent = changesSplitter
    diffSplitter.secondComponent = frameDiffPreview?.component

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
        filterProgress.startLoading(false)
        activityList.updateEmptyText(true)
      }

      override fun onFilteringStopped(result: Set<ActivityItem>?) {
        filterProgress.stopLoading()
        activityList.setVisibleItems(result)
        activityList.updateEmptyText(false)
      }
    }, this)
    if (isSwitchingDiffModeAllowed) {
      settings.addListener(object : ActivityViewApplicationSettings.Listener {
        override fun settingsChanged() {
          model.diffMode = settings.diffMode
        }
      }, this)
    }
    model.diffMode = currentDiffMode

    isFocusCycleRoot = true
    focusTraversalPolicy = object : ComponentsListFocusTraversalPolicy() {
      override fun getOrderedComponents(): List<Component> {
        return listOfNotNull(activityList, changesBrowser?.preferredFocusedComponent, searchField.textComponent,
                             frameDiffPreview?.preferredFocusedComponent)
      }
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ActivityViewDataKeys.SELECTION] = activityList.selection
    sink[ActivityViewDataKeys.SCOPE] = activityScope
    sink[EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW] = editorDiffPreview
    sink[ActivityViewDataKeys.DIRECTORY_DIFF_MODE] = model.diffMode
  }

  val preferredFocusedComponent: JComponent get() = activityList

  private fun createChangesBrowser(): ActivityChangesBrowser? {
    if (model.isSingleDiffSupported) return null

    val changesBrowser = ActivityChangesBrowser(project, isSwitchingDiffModeAllowed, searchField.textComponent)
    model.addListener(object : ActivityModelListener {
      override fun onDiffDataLoadingStarted() {
        changesBrowser.loadingStarted()
      }

      override fun onDiffDataLoadingStopped(diffData: ActivityDiffData?) {
        changesBrowser.loadingFinished(diffData)
      }
    }, changesBrowser)
    changesBrowser.loadingStarted()
    Disposer.register(this, changesBrowser)
    return changesBrowser
  }

  private fun createEditorDiffPreview(changesBrowser: ActivityChangesBrowser?): EditorTabDiffPreview {
    if (changesBrowser != null) {
      val diffPreview = MultiFileActivityDiffPreview(activityScope, changesBrowser.viewer, this@ActivityView)
      changesBrowser.setShowDiffActionPreview(diffPreview)
      if (model.filterKind == FilterKind.CONTENT) diffPreview.addListener(DiffRequestProcessorListener(::updateDiffEditorSearch), this@ActivityView)
      return diffPreview
    }

    val diffPreview = SingleFileActivityDiffPreview(project, model, this@ActivityView)
    if (model.filterKind == FilterKind.CONTENT) diffPreview.addListener(DiffRequestProcessorListener(::updateDiffEditorSearch), this@ActivityView)
    return diffPreview
  }

  private fun createFrameDiffPreview(changesBrowser: ActivityChangesBrowser?): DiffEditorViewer {
    val diffViewer = if (changesBrowser != null) {
      TreeHandlerEditorDiffPreview.createDefaultViewer(changesBrowser.viewer, ActivityDiffPreviewHandler(), DIFF_PLACE)
    }
    else {
      SingleFileActivityDiffPreview.createViewer(project, model)
    }
    Disposer.register(this, diffViewer.disposable)
    if (model.filterKind == FilterKind.CONTENT && diffViewer is DiffRequestProcessor) {
      diffViewer.addListener(DiffRequestProcessorListener(::updateDiffEditorSearch), this@ActivityView)
    }
    return diffViewer
  }

  private fun createSearchField(): SearchFieldComponent {
    val searchField = when (model.filterKind) {
      FilterKind.FILE -> SearchFieldComponent.SingleLine().also { field ->
        field.textComponent.emptyText.text = LocalHistoryBundle.message("activity.filter.empty.text.fileName")
      }
      FilterKind.CONTENT -> SearchFieldComponent.MultiLine().also { field ->
        field.containerComponent.setBorder(JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.RIGHT),
                                                                 field.containerComponent.border))
        field.textComponent.emptyText.text = LocalHistoryBundle.message("activity.filter.empty.text.content")

        dumbAwareAction { selectNextOccurence(true) }.registerCustomShortcutSet(Utils.shortcutSetOf(
          Utils.shortcutsOf(IdeActions.ACTION_FIND_NEXT) + Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        ), field.containerComponent)
        dumbAwareAction { selectNextOccurence(false) }.registerCustomShortcutSet(Utils.shortcutSetOf(
          Utils.shortcutsOf(IdeActions.ACTION_FIND_PREVIOUS) + Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        ), field.containerComponent)
      }
    }
    TextComponentEmptyText.setupPlaceholderVisibility(searchField.textComponent)
    dumbAwareAction {
      IdeFocusManager.getInstance(project).requestFocus(searchField.textComponent, true)
    }.registerCustomShortcutSet(Utils.shortcutSetOf(Utils.shortcutsOf(IdeActions.ACTION_FIND)), activityList)
    dumbAwareAction {
      searchField.textComponent.text = ""
      IdeFocusManager.getInstance(project).requestFocus(activityList, true)
    }.registerCustomShortcutSet(CustomShortcutSet(KeyEvent.VK_ESCAPE), searchField.textComponent)
    searchField.textComponent.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (!model.isFilterSet) LocalHistoryCounter.logFilterUsed(activityScope)
        model.setFilter(searchField.textComponent.text)
      }
    })
    return searchField
  }

  private fun getDiffComponent(): JComponent? {
    if (editorDiffPreview == null) return frameDiffPreview?.component
    val previewFile = editorDiffPreview.previewFile
    val editors = FileEditorManager.getInstance(project).getEditors(previewFile)
    return editors.firstOrNull()?.component
  }

  private fun updateDiffEditorSearch() {
    val diffComponent = getDiffComponent() ?: return
    val editor = FileHistoryDialog.findLeftEditor(diffComponent) ?: return

    FileHistoryDialog.updateEditorSearch(project, searchField.textComponent, editor)
  }

  private fun selectNextOccurence(forward: Boolean) {
    val diffComponent = getDiffComponent() ?: return
    val editor = FileHistoryDialog.findLeftEditor(diffComponent) ?: return
    val session = EditorSearchSession.get(editor) ?: return

    if (session.hasMatches()) {
      if (session.isLast(forward)) activityList.moveSelection(forward)
      else if (forward) session.searchForward()
      else session.searchBackward()
    }
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
      activityView.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP))

      val dialog = FrameWrapper(project,
                                title = LocalHistoryBundle.message("activity.dialog.title", activityScope.presentableName),
                                component = activityView,
                                dimensionKey = "lvcs.dialog.size")
      dialog.closeOnEsc()
      Disposer.register(dialog, activityView)
      dialog.show()
    }

    @JvmStatic
    fun isViewEnabled(): Boolean = isViewAvailable()

    @JvmStatic
    fun isViewAvailable(): Boolean = Registry.`is`("lvcs.show.activity.view")
  }
}

@Service(Service.Level.PROJECT)
internal class ActivityService(val coroutineScope: CoroutineScope)

private fun dumbAwareAction(runnable: () -> Unit): DumbAwareAction {
  return object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = runnable()
  }
}

private sealed interface SearchFieldComponent {
  val containerComponent: JPanel
  val textComponent: JTextComponent

  class SingleLine : SearchFieldComponent {
    override val containerComponent = SearchTextField("Lvcs.FileFilter.History")
    override val textComponent: JBTextField get() = containerComponent.textEditor
  }

  class MultiLine : SearchFieldComponent {
    private val textArea = JBTextArea()
    override val containerComponent = SearchTextArea(textArea, true)
    override val textComponent = textArea
  }
}