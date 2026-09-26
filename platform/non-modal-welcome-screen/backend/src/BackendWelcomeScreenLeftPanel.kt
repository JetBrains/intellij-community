// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(AwaitCancellationAndInvoke::class)

package com.intellij.platform.ide.nonModalWelcomeScreen.backend

import com.intellij.icons.AllIcons.Actions.Search
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.setupTransfer
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectCollectors
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectFilteringTree
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory
import com.intellij.platform.ide.nonModalWelcomeScreen.DefaultFileDragAndDropHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WELCOME_SCREEN_IS_SHOWN
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelActions
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelRpc
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal class WelcomeScreenLeftPanelRpcProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<WelcomeScreenLeftPanelRpc>()) {
      WelcomeScreenLeftPanelRpcImpl()
    }
  }
}

internal class WelcomeScreenLeftPanelRpcImpl : WelcomeScreenLeftPanelRpc {
  override suspend fun getComponentId(projectId: ProjectId): ComponentDirectTransferId {
    return withContext(Dispatchers.UI) {
      BackendWelcomeScreenLeftPanelService.getInstance(projectId.findProject()).createPanel()
    }
  }
}

@Service(Level.PROJECT)
internal class BackendWelcomeScreenLeftPanelService(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): BackendWelcomeScreenLeftPanelService = project.service()
  }

  @RequiresEdt
  fun createPanel(): ComponentDirectTransferId {
    val disposable = Disposer.newDisposable("BackendWelcomeScreenLeftPanelService.createPanel")
    scope.awaitCancellationAndInvoke {
      withContext(Dispatchers.UI) {
        Disposer.dispose(disposable)
      }
    }
    return BackendWelcomeScreenLeftPanel(project, scope).createComponent().setupTransfer(disposable)
  }
}

internal class BackendWelcomeScreenLeftPanel(
  private val project: Project,
  private val scope: CoroutineScope,
) {
  private lateinit var searchField: SearchTextField

  private fun setupDragAndDrop(component: JComponent) {
    val target = object : DnDNativeTarget {
      override fun update(event: DnDEvent): Boolean {
        if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
          return false
        }
        event.isDropPossible = true
        return false
      }

      override fun drop(event: DnDEvent) {
        val files = FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject)
          .map { file -> file.toPath() }
        val handler = WelcomeRightTabContentProvider.getSingleExtension()?.getFileDragAndDropHandler()
                      ?: DefaultFileDragAndDropHandler
        handler.openFiles(project, files)
      }
    }

    DnDSupport.createBuilder(component)
      .enableAsNativeTarget()
      .setTargetChecker(target)
      .setDropHandler(target)
      .setDisposableParent(scope.asDisposable())
      .install()
  }

  fun createComponent(): JComponent {
    val mainPanel = MyMainPanel()

    val projectFilteringTree = createRecentProjectTree()
    setupDragAndDrop(projectFilteringTree.component)

    val topPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.empty()
    }
    topPanel.add(WelcomeScreenLeftPanelActions(project).createButtonsComponent(scope))
    topPanel.add(separator { customize(UnscaledGapsY(top = 17)) })
    topPanel.add(searchPanel(projectFilteringTree))
    topPanel.add(separator())

    mainPanel.add(topPanel, BorderLayout.NORTH)
    mainPanel.add(createScrollPane(projectFilteringTree.component, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER, true),
                  BorderLayout.CENTER)

    mainPanel.isFocusTraversalPolicyProvider = true
    mainPanel.focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getDefaultComponent(aContainer: Container): Component {
        return searchField.textEditor
      }
    }

    return mainPanel
  }

  private fun searchPanel(recentProjectTree: RecentProjectFilteringTree) = panel {
    row {
      val projectSearch = createProjectSearchField(recentProjectTree)
      searchField = projectSearch
      cell(projectSearch)
        .align(AlignX.FILL)
        .customize(UnscaledGaps(top = 4, bottom = 4, left = 20, right = 20))
    }
  }

  private fun createRecentProjectTree(): RecentProjectFilteringTree =
    RecentProjectPanelComponentFactory.createComponent(
      scope.asDisposable(),
      collectors = listOf(ProjectCollectors.cloneableProjectsCollector, ProjectCollectors.createRecentProjectsWithoutCurrentCollector(project)),
      treeBackground = null
    ).apply {
      tree.emptyText.text = NonModalWelcomeScreenBundle.message("welcome.screen.no.recent.projects")
      selectLastOpenedProjectOrTheFirstInTree()
    }

  private fun createProjectSearchField(recentProjectTree: RecentProjectFilteringTree): SearchTextField =
    recentProjectTree.installSearchField().apply {
      if (ExperimentalUI.isNewUI()) {
        textEditor.putClientProperty("JTextField.Search.Icon", Search)
      }
    }

  private fun separator(customize: Row.() -> Unit = {}) = panel {
    separator().customize()
  }

  private class MyMainPanel : JBPanel<JBPanel<*>>(BorderLayout()), UiDataProvider {
    init {
      border = JBUI.Borders.empty()
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[WELCOME_SCREEN_IS_SHOWN] = true
    }
  }
}
