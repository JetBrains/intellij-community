// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.find.SearchTextArea
import com.intellij.find.editorHeaderActions.Utils
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ReportFeedbackService
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.settings.ActivityViewApplicationSettings
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent

class ActivityView(private val project: Project, gateway: IdeaGateway, val activityScope: ActivityScope) :
  JBPanel<ActivityView>(BorderLayout()), DataProvider, Disposable {

  private val coroutineScope = project.service<ActivityService>().coroutineScope.childScope()

  private val model = ActivityViewModel(project, gateway, activityScope, coroutineScope)

  private val activityList = ActivityList { model.activityProvider.getPresentation(it) }.apply {
    updateEmptyText(true)
  }
  private val editorDiffPreview = object : CombinedActivityDiffPreview(project, activityList, activityScope, this@ActivityView) {
    override fun loadDiffDataSynchronously() = model.loadDiffDataSynchronously()

    override fun returnFocusToSourceComponent() {
      IdeFocusManager.getInstance(project).requestFocus(activityList, true)
    }
  }

  init {
    PopupHandler.installPopupMenu(activityList, "ActivityView.Popup", "ActivityView.Popup")
    val scrollPane = ScrollPaneFactory.createScrollPane(activityList,
                                                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    val progressStripe = ProgressStripe(scrollPane, this)
    add(progressStripe, BorderLayout.CENTER)

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

    val notificationPanel = createNotificationPanel()
    if (notificationPanel != null) toolbarComponent.add(notificationPanel, BorderLayout.NORTH)

    add(toolbarComponent, BorderLayout.NORTH)

    activityList.addListener(object : ActivityList.Listener {
      override fun onSelectionChanged(selection: ActivitySelection) {
        model.setSelection(selection)
      }
      override fun onEnter(): Boolean {
        showDiff()
        return true
      }
      override fun onDoubleClick(): Boolean {
        showDiff()
        return true
      }
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
      override fun onDiffDataLoaded(diffData: ActivityDiffData?) {
        editorDiffPreview.setDiffData(diffData)
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

  private fun createNotificationPanel(): EditorNotificationPanel? {
    if (PropertiesComponent.getInstance().getBoolean(NOTIFICATION_DISMISSED_KEY)) return null

    val notificationPanel = EditorNotificationPanel(JBUI.CurrentTheme.Banner.INFO_BACKGROUND)
    notificationPanel.text = LocalHistoryBundle.message("activity.notification.text")
    notificationPanel.border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.BOTTOM), notificationPanel.border)

    notificationPanel.createActionLabel(LocalHistoryBundle.message("activity.notification.feedback.link")) {
      service<ReportFeedbackService>().coroutineScope.launch {
        withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
          val description = SendFeedbackAction.getDescription(project)
          BrowserUtil.browse(URL_PREFIX + URLEncoder.encode(description, StandardCharsets.UTF_8), project)
        }
      }
    }
    notificationPanel.add(InplaceButton(IconButton(LocalHistoryBundle.message("activity.notification.dismiss.tooltip"),
                                                   AllIcons.Actions.Close, AllIcons.Actions.CloseHovered)) {
      PropertiesComponent.getInstance().setValue(NOTIFICATION_DISMISSED_KEY, true)
      notificationPanel.parent?.remove(notificationPanel)
    }, BorderLayout.EAST)

    val disableLabel = JLabel(LocalHistoryBundle.message("activity.notification.disable.text",
                                                         ShowSettingsUtil.getSettingsMenuName(),
                                                         LocalHistoryBundle.message("activity.configurable.title")))
    disableLabel.foreground = EditorColorsManager.getInstance().getGlobalScheme().defaultForeground
    disableLabel.border = JBUI.Borders.emptyTop(2)
    notificationPanel.add(disableLabel, BorderLayout.SOUTH)

    return notificationPanel
  }

  private fun ActivityList.updateEmptyText(isLoading: Boolean) = setEmptyText(getEmptyText(isLoading))

  private fun getEmptyText(isLoading: Boolean): @NlsContexts.StatusText String {
    if (isLoading) return LocalHistoryBundle.message("activity.list.empty.text.loading")
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

  internal fun showDiff() = editorDiffPreview.performDiffAction()

  override fun dispose() {
    coroutineScope.cancel()
  }

  companion object {
    private const val NOTIFICATION_DISMISSED_KEY = "lvcs.experimental.ui.notification.dismissed"
    private const val URL_PREFIX = "https://youtrack.jetbrains.com/newIssue?project=IDEA&c=Subsystem+Version+Control.+Local+History&c=Type+Support+Request&description="

    @JvmStatic
    fun show(project: Project, gateway: IdeaGateway, activityScope: ActivityScope) {
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
    }

    private fun ActivityView.openDiffWhenLoaded() {
      val disposable = Disposer.newDisposable()
      Disposer.register(this, disposable)
      model.addListener(object : ActivityModelListener {
        override fun onItemsLoadingStopped(data: ActivityData) {
          if (data.items.isEmpty()) Disposer.dispose(disposable)
        }

        override fun onDiffDataLoaded(diffData: ActivityDiffData?) {
          if (diffData != null) {
            showDiff()
            Disposer.dispose(disposable)
          }
        }
      }, disposable)
    }

    @JvmStatic
    fun isViewEnabled(): Boolean {
      if (!isViewAvailable()) return false
      return service<ActivityViewApplicationSettings>().isActivityToolWindowEnabled
    }

    @JvmStatic
    fun isViewAvailable(): Boolean {
      if (AppMode.isRemoteDevHost()) return false
      return Registry.`is`("lvcs.show.activity.view")
    }
  }
}

@Service(Service.Level.PROJECT)
class ActivityService(val coroutineScope: CoroutineScope)
