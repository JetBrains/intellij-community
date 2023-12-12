// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.find.SearchTextArea
import com.intellij.find.editorHeaderActions.Utils
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.ui.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class ActivityView(private val project: Project, gateway: IdeaGateway, val activityScope: ActivityScope) :
  JBPanel<ActivityView>(BorderLayout()), DataProvider, Disposable {

  private val coroutineScope = project.service<ActivityService>().coroutineScope.childScope()

  private val model = ActivityViewModel(project, gateway, activityScope, coroutineScope)

  private val activityList = ActivityList { model.activityProvider.getPresentation(it) }.apply {
    updateEmptyText(true)
  }
  private val editorDiffPreview = object : CombinedActivityDiffPreview(project, activityList, activityScope, this@ActivityView) {
    override fun returnFocusToSourceComponent() {
      IdeFocusManager.getInstance(project).requestFocus(activityList, true)
    }
  }

  init {
    PopupHandler.installPopupMenu(activityList, "ActivityView.Popup", "ActivityView.Popup")
    val scrollPane = ScrollPaneFactory.createScrollPane(activityList).apply { border = IdeBorderFactory.createBorder(SideBorder.TOP) }
    val progressStripe = ProgressStripe(scrollPane, this)
    add(progressStripe, BorderLayout.CENTER)

    val toolbarComponent = BorderLayoutPanel()

    val filterProgress = if (model.isFilterSupported) {
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

    add(toolbarComponent, BorderLayout.NORTH)

    activityList.addListener(object : ActivityList.Listener {
      override fun onSelectionChanged(selection: ActivitySelection) {
        model.setSelection(selection)
      }
      override fun onEnter(): Boolean {
        editorDiffPreview.openPreview(true)
        return true
      }
      override fun onDoubleClick(): Boolean {
        editorDiffPreview.openPreview(true)
        return true
      }
    }, this)
    model.addListener(object : ActivityModelListener {
      override fun onItemsLoadingStarted() {
        activityList.updateEmptyText(true)
        progressStripe.startLoading()
      }
      override fun onItemsLoadingStopped(items: List<ActivityItem>) {
        activityList.setItems(items)
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
    val searchTextArea = SearchTextArea(JBTextArea(), true)
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
        model.setFilter(searchTextArea.textArea.getText())
      }
    })
    return searchTextArea
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

  override fun dispose() {
    coroutineScope.cancel()
  }

  companion object {
    @JvmStatic
    fun show(project: Project, gateway: IdeaGateway, activityScope: ActivityScope) {
      if (ActivityToolWindow.showTab(project) { content -> (content.component as? ActivityView)?.activityScope == activityScope }) {
        return
      }

      val activityView = ActivityView(project, gateway, activityScope)

      val content = ContentFactory.getInstance().createContent(activityView, activityScope.presentableName, false)
      content.preferredFocusableComponent = activityView.preferredFocusedComponent
      content.setDisposer(activityView)

      ActivityToolWindow.showTab(project, content)
    }

    @JvmStatic
    fun isViewEnabled() = Registry.`is`("lvcs.show.activity.view")
  }
}

@Service(Service.Level.PROJECT)
class ActivityService(val coroutineScope: CoroutineScope)
